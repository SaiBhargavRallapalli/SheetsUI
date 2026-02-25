package com.rsb.sheetsui.data.auth

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.rsb.sheetsui.R
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val UNVERIFIED_APP_HINT =
    " If you see 'Unverified app', tap Advanced → Go to SheetsUI."

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val firebaseAuth: FirebaseAuth
) {
    private val credentialManager = CredentialManager.create(appContext)

    /** When true, skip One Tap and use only Sign In With Google (avoids "Account reauth failed" / error 16). */
    @Volatile
    private var skipOneTapNextTime = true  // Start true – One Tap often fails with error 16 after package change or stale creds

    private val webClientId: String
        get() = appContext.getString(R.string.default_web_client_id)

    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    val isSignedIn: Boolean
        get() = currentUser != null

    /**
     * Set by the Activity before sign-in to handle OAuth consent screens.
     * The lambda launches the consent Intent via a registered ActivityResultLauncher.
     */
    @Volatile
    var consentLauncher: ((Intent) -> Unit)? = null

    private var consentDeferred: CompletableDeferred<Unit>? = null

    /** Called by the Activity when the consent screen finishes. */
    fun onConsentResult(granted: Boolean) {
        if (granted) {
            consentDeferred?.complete(Unit)
        } else {
            consentDeferred?.completeExceptionally(Exception("Additional permissions were not granted."))
        }
    }

    /**
     * Full sign-in flow:
     * 1. Clear credential state (avoids "Account reauth failed" / error 16 from stale credentials)
     * 2. Credential Manager → Firebase Auth (identity)
     * 3. GoogleAuthUtil.getToken → ensure OAuth scopes are consented (authorization)
     */
    suspend fun signIn(activityContext: Context): Result<FirebaseUser> {
        return try {
            clearCredentialState()
            val authResult = authenticateWithFirebase(activityContext)
            if (authResult.isFailure) {
                val ex = authResult.exceptionOrNull()
                if (ex != null && isReauthRelatedError(ex)) skipOneTapNextTime = true
                return authResult
            }

            val user = authResult.getOrThrow()

            // Ensure the user has consented to Drive + Sheets OAuth scopes
            val scopeResult = ensureOAuthScopes(activityContext)
            if (scopeResult.isFailure) {
                Log.e(TAG, "OAuth scope authorization failed", scopeResult.exceptionOrNull())
                return Result.failure(
                    scopeResult.exceptionOrNull()
                        ?: Exception("OAuth scope authorization failed")
                )
            }

            Result.success(user).also { skipOneTapNextTime = false }
        } catch (e: GetCredentialException) {
            Log.e(TAG, "GetCredentialException during sign-in", e)
            if (isReauthRelatedError(e)) skipOneTapNextTime = true
            Result.failure(Exception(getUserFriendlyReauthMessage(e)))
        }
    }

    private fun getUserFriendlyReauthMessage(e: GetCredentialException): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            msg.contains("16") || msg.contains("reauth") -> {
                "Account reauth failed. Sign out from this app, then sign in again. " +
                    "If it persists, remove SheetsUI from Settings → Google → Your connections to third-party apps, then retry."
            }
            else -> "Please Sign In Again"
        }
    }

    private suspend fun clearCredentialState() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
            Log.d(TAG, "Cleared credential state before sign-in")
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear credential state: ${e.message}")
        }
    }

    /**
     * Two-phase Credential Manager sign-in:
     *   Phase 1 – one-tap (saved credentials), skipped if skipOneTapNextTime (after previous reauth error)
     *   Phase 2 – full Sign In With Google (most reliable for "Account reauth failed")
     */
    private suspend fun authenticateWithFirebase(
        activityContext: Context
    ): Result<FirebaseUser> {
        if (skipOneTapNextTime) {
            Log.d(TAG, "Skipping One Tap (previous reauth error) – using Sign In With Google only")
            clearCredentialState()
            return attemptSignInWithGoogle(activityContext)
        }

        val phase1 = attemptGoogleIdSignIn(activityContext)
        if (phase1.isSuccess) return phase1

        val phase1Error = phase1.exceptionOrNull()
        when (phase1Error) {
            is NoCredentialException ->
                Log.d(TAG, "No saved credentials, falling back to Sign In With Google")
            is GetCredentialCancellationException ->
                Log.d(TAG, "User cancelled one-tap, falling back to Sign In With Google")
            else ->
                Log.e(TAG, "Phase 1 failed: ${phase1Error?.javaClass?.simpleName}", phase1Error)
        }

        // Reauth/error 16 from One Tap: clear state again before full flow to avoid stale cache
        if (phase1Error != null && isReauthRelatedError(phase1Error)) {
            Log.d(TAG, "Reauth-related error from One Tap – clearing state before full sign-in")
            clearCredentialState()
        }

        return attemptSignInWithGoogle(activityContext)
    }

    private fun isReauthRelatedError(e: Throwable): Boolean {
        val msg = e.message?.lowercase() ?: return false
        return msg.contains("16") || msg.contains("reauth") || msg.contains("account reauth")
    }

    // ── OAuth scope authorization ───────────────────────────────────────

    /**
     * Requests an OAuth2 access token with Drive + Sheets scopes.
     * If the user hasn't consented yet, [UserRecoverableAuthException] is thrown
     * and we launch the consent screen, await the result, then retry.
     */
    private suspend fun ensureOAuthScopes(context: Context): Result<Unit> = runCatching {
        val email = firebaseAuth.currentUser?.email
            ?: throw IllegalStateException("No signed-in user email")
        val account = Account(email, "com.google")

        withContext(Dispatchers.IO) {
            try {
                val token = GoogleAuthUtil.getToken(context, account, OAUTH_SCOPES)
                Log.d(TAG, "OAuth access token obtained – length=${token.length}")
            } catch (e: UserRecoverableAuthException) {
                Log.w(TAG, "OAuth consent required – launching consent screen")
                val consentIntent = e.intent
                    ?: throw IllegalStateException("UserRecoverableAuthException has no intent")
                requestUserConsent(consentIntent)
                // Retry after the user grants consent
                val token = GoogleAuthUtil.getToken(context, account, OAUTH_SCOPES)
                Log.d(TAG, "OAuth access token after consent – length=${token.length}")
            }
        }
    }

    private suspend fun requestUserConsent(consentIntent: Intent) {
        val launcher = consentLauncher
            ?: throw IllegalStateException(
                "consentLauncher not set – call authManager.consentLauncher = ... in your Activity"
            )

        consentDeferred = CompletableDeferred()
        withContext(Dispatchers.Main) { launcher(consentIntent) }
        consentDeferred?.await()
    }

    // ── Credential Manager helpers ──────────────────────────────────────

    private suspend fun attemptGoogleIdSignIn(
        activityContext: Context
    ): Result<FirebaseUser> = runCatching {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false) // Avoids "Account reauth failed" (16) from auto-using stale credentials
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val result = credentialManager.getCredential(activityContext, request)
        firebaseAuthWithCredential(result.credential)
    }.onFailure { e ->
        handleCredentialException(activityContext, e, "attemptGoogleIdSignIn")
    }

    private suspend fun attemptSignInWithGoogle(
        activityContext: Context
    ): Result<FirebaseUser> = runCatching {
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val result = credentialManager.getCredential(activityContext, request)
        firebaseAuthWithCredential(result.credential)
    }.onFailure { e ->
        handleCredentialException(activityContext, e, "attemptSignInWithGoogle")
    }

    private suspend fun handleCredentialException(
        context: Context,
        e: Throwable,
        phase: String
    ) {
        Log.e(TAG, "$phase failed: ${e.javaClass.simpleName} – ${e.message}", e)
        if (e is GetCredentialException) {
            val userMessage = getUserFriendlyReauthMessage(e)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "$userMessage$UNVERIFIED_APP_HINT",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun firebaseAuthWithCredential(
        credential: androidx.credentials.Credential
    ): FirebaseUser {
        Log.d(TAG, "Credential type received: ${credential.type}")

        val googleIdToken = when {
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL -> {
                GoogleIdTokenCredential.createFrom(credential.data)
            }
            credential is androidx.credentials.CustomCredential -> {
                GoogleIdTokenCredential.createFrom(credential.data)
            }
            else -> throw IllegalStateException("Unexpected credential type: ${credential.type}")
        }

        val idToken = googleIdToken.idToken
        Log.d(TAG, "ID token received: ${idToken.take(10)}…")

        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        Log.d(TAG, "Exchanging ID token with Firebase…")
        val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
        val user = authResult.user
            ?: throw IllegalStateException("Firebase sign-in succeeded but user is null")
        Log.d(TAG, "Firebase sign-in successful: uid=${user.uid}, name=${user.displayName}")
        return user
    }

    suspend fun signOut() {
        firebaseAuth.signOut()
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    /**
     * Invalidates the given OAuth token so the next [GoogleAuthUtil.getToken] call
     * fetches a fresh token. Call before attempting token refresh on 401.
     */
    @Suppress("DEPRECATION")
    fun invalidateOAuthToken(context: Context, token: String) {
        try {
            GoogleAuthUtil.clearToken(context, token)
            Log.d(TAG, "Invalidated OAuth token for refresh")
        } catch (e: Exception) {
            Log.w(TAG, "Could not clear token (may already be invalid): ${e.message}")
        }
    }

    /**
     * Requests OAuth scope consent via the consent launcher.
     * Used by AuthInterceptor when 401 triggers UserRecoverableAuthException.
     */
    suspend fun requestOAuthConsent(intent: Intent) = requestUserConsent(intent)

    companion object {
        private const val TAG = "AuthDebug"

        const val OAUTH_SCOPES =
            "oauth2:https://www.googleapis.com/auth/drive " +
            "https://www.googleapis.com/auth/spreadsheets " +
            "https://www.googleapis.com/auth/forms.body"
    }
}
