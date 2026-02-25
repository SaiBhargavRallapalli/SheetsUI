package com.rsb.sheetsui.presentation.login

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rsb.sheetsui.data.auth.GoogleAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authManager: GoogleAuthManager
) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(
        if (authManager.isSignedIn) {
            LoginState.Success(authManager.currentUser?.displayName).also {
                Log.d(TAG, "Init: already signed in as ${authManager.currentUser?.displayName}")
            }
        } else {
            LoginState.Idle.also { Log.d(TAG, "Init: not signed in") }
        }
    )
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun signIn(activityContext: Context) {
        if (_state.value is LoginState.Loading) return
        setState(LoginState.Loading)

        viewModelScope.launch {
            authManager.signIn(activityContext)
                .onSuccess { user ->
                    Log.d(TAG, "signIn SUCCESS: uid=${user.uid}, name=${user.displayName}")
                    setState(LoginState.Success(user.displayName))
                }
                .onFailure { throwable ->
                    Log.e(TAG, "signIn FAILURE: ${throwable.javaClass.simpleName}", throwable)
                    setState(
                        LoginState.Error(
                            throwable.localizedMessage ?: "Sign-in failed. Please try again."
                        )
                    )
                }
        }
    }

    fun resetError() {
        if (_state.value is LoginState.Error) {
            setState(LoginState.Idle)
        }
    }

    private fun setState(newState: LoginState) {
        Log.d(TAG, "State: ${_state.value::class.simpleName} → ${newState::class.simpleName}")
        _state.value = newState
    }

    companion object {
        private const val TAG = "AuthDebug"
    }
}
