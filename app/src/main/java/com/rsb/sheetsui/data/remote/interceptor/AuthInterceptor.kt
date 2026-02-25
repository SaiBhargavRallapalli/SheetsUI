package com.rsb.sheetsui.data.remote.interceptor

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.rsb.sheetsui.data.auth.GoogleAuthManager
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthInterceptor"

/**
 * OkHttp interceptor that attaches a Google OAuth2 access token to every
 * outgoing request. On 401, invalidates the token, attempts silent refresh
 * or OAuth consent, then retries once with a new token.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firebaseAuth: FirebaseAuth,
    private val authManager: GoogleAuthManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val email = firebaseAuth.currentUser?.email

        val token = getAccessToken(email)
        val request = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(request)

        // 401: token expired or invalid – try refresh and retry once
        if (response.code == 401 && token != null && email != null) {
            response.close()
            val newToken = refreshTokenAndRetry(email, token)
            if (newToken != null) {
                val retryRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return chain.proceed(retryRequest)
            }
        }

        return response
    }

    private fun getAccessToken(email: String?): String? {
        if (email == null) {
            Log.w(TAG, "No signed-in user, skipping auth header")
            return null
        }
        return try {
            val account = Account(email, "com.google")
            val accessToken = GoogleAuthUtil.getToken(
                context, account, GoogleAuthManager.OAUTH_SCOPES
            )
            Log.d(TAG, "OAuth token for $email – length=${accessToken.length}")
            accessToken
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get OAuth token for $email: ${e.javaClass.simpleName}", e)
            null
        }
    }

    private fun refreshTokenAndRetry(email: String, oldToken: String): String? {
        Log.d(TAG, "401 received – invalidating token and refreshing")
        authManager.invalidateOAuthToken(context, oldToken)

        return runBlocking {
            try {
                withContext(Dispatchers.IO) {
                    val account = Account(email, "com.google")
                    GoogleAuthUtil.getToken(context, account, GoogleAuthManager.OAUTH_SCOPES)
                }.also { Log.d(TAG, "Silent token refresh succeeded") }
            } catch (e: UserRecoverableAuthException) {
                Log.w(TAG, "OAuth consent required for refresh – launching consent")
                val intent = e.intent ?: return@runBlocking null
                authManager.requestOAuthConsent(intent)
                withContext(Dispatchers.IO) {
                    try {
                        val account = Account(email, "com.google")
                        GoogleAuthUtil.getToken(context, account, GoogleAuthManager.OAUTH_SCOPES)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Token fetch after consent failed", e2)
                        null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh failed: ${e.javaClass.simpleName}", e)
                null
            }
        }
    }
}
