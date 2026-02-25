package com.rsb.sheetsui.presentation.login

/**
 * Represents every possible state of the login screen.
 */
sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data class Success(val displayName: String?) : LoginState
    data class Error(val message: String) : LoginState
}
