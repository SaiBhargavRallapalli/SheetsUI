package com.rsb.sheetsui.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onCompanyProfile: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val privacyLock by viewModel.privacyLockEnabled.collectAsState(initial = false)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showBiometricPrompt by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text("Privacy Lock") },
                supportingContent = {
                    Text(
                        "Require fingerprint or face to open the app",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = privacyLock,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val bm = BiometricManager.from(context)
                                when (bm.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
                                    BiometricManager.BIOMETRIC_SUCCESS -> showBiometricPrompt = true
                                    else -> {
                                        scope.launch { viewModel.setPrivacyLock(false) }
                                        Toast.makeText(context, "Add fingerprint or device PIN in Settings first.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                scope.launch { viewModel.setPrivacyLock(false) }
                            }
                        }
                    )
                }
            )
            ListItem(
                headlineContent = { Text("Company Profile") },
                supportingContent = {
                    Text(
                        "Logo and primary color",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.Palette, contentDescription = null)
                },
                trailingContent = {},
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable(onClick = onCompanyProfile)
            )
        }
    }

    LaunchedEffect(showBiometricPrompt) {
        if (!showBiometricPrompt) return@LaunchedEffect
        if (context !is FragmentActivity) {
            Toast.makeText(context, "Cannot show biometric prompt.", Toast.LENGTH_SHORT).show()
            showBiometricPrompt = false
            return@LaunchedEffect
        }
        val activity = context as FragmentActivity
        val executor = ContextCompat.getMainExecutor(activity)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Privacy Lock")
            .setSubtitle("Use fingerprint or device PIN to secure the app")
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()
        val bioPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    scope.launch { viewModel.setPrivacyLock(true) }
                }
                override fun onAuthenticationFailed() {
                    scope.launch { viewModel.setPrivacyLock(false) }
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    scope.launch { viewModel.setPrivacyLock(false) }
                    if (errorCode != 10) {
                        Toast.makeText(context, "Privacy Lock: $errString", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        bioPrompt.authenticate(promptInfo)
        showBiometricPrompt = false
    }
}
