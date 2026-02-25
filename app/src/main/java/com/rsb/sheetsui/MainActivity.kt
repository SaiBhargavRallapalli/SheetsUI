package com.rsb.sheetsui

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rsb.sheetsui.data.auth.GoogleAuthManager
import com.rsb.sheetsui.data.prefs.CompanyPreferences
import com.rsb.sheetsui.presentation.detail.SheetDetailScreen
import com.rsb.sheetsui.presentation.edit.AddRowScreen
import com.rsb.sheetsui.presentation.edit.EditRowScreen
import com.rsb.sheetsui.presentation.profile.CompanyProfileScreen
import com.rsb.sheetsui.presentation.settings.SettingsScreen
import com.rsb.sheetsui.presentation.home.HomeScreen
import com.rsb.sheetsui.presentation.login.LoginScreen
import com.rsb.sheetsui.ui.theme.SheetsUITheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authManager: GoogleAuthManager

    @Inject
    lateinit var companyPreferences: CompanyPreferences

    private val oauthConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val granted = result.resultCode == Activity.RESULT_OK
        Log.d("AuthDebug", "OAuth consent result: granted=$granted")
        authManager.onConsentResult(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authManager.consentLauncher = { intent -> oauthConsentLauncher.launch(intent) }

        enableEdgeToEdge()
        setContent {
            val companyPrimaryColor by companyPreferences.primaryColor.collectAsState(initial = null)
            SheetsUITheme(companyPrimaryColor = companyPrimaryColor) {
                val navController = rememberNavController()
                val scope = rememberCoroutineScope()
                val startDest = if (authManager.isSignedIn) Route.HOME else Route.LOGIN

                NavHost(navController = navController, startDestination = startDest) {

                    composable(Route.LOGIN) {
                        LoginScreen(
                            onLoginSuccess = {
                                navController.navigate(Route.HOME) {
                                    popUpTo(Route.LOGIN) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(Route.HOME) {
                        HomeScreen(
                            displayName = authManager.currentUser?.displayName,
                            onSignOut = {
                                scope.launch {
                                    authManager.signOut()
                                    navController.navigate(Route.LOGIN) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            },
                            onSheetClick = { sheet ->
                                val encodedName = Uri.encode(sheet.name)
                                navController.navigate("detail/${sheet.id}?name=$encodedName")
                            },
                            onSettings = { navController.navigate(Route.SETTINGS) }
                        )
                    }

                    composable(
                        route = "detail/{spreadsheetId}?name={name}",
                        arguments = listOf(
                            navArgument("spreadsheetId") { type = NavType.StringType },
                            navArgument("name") { type = NavType.StringType; defaultValue = "Spreadsheet" }
                        )
                    ) { backStackEntry ->
                        val savedStateHandle = backStackEntry.savedStateHandle
                        val shouldRefresh = savedStateHandle.get<Boolean>("refresh") == true
                        val sid = backStackEntry.arguments?.getString("spreadsheetId") ?: ""
                        val name = backStackEntry.arguments?.getString("name") ?: "Spreadsheet"

                        SheetDetailScreen(
                            onBack = { navController.popBackStack() },
                            onRowClick = { rowIndex, sheetName ->
                                navController.navigate("edit/$sid/$rowIndex?name=${Uri.encode(name)}&sheetName=${Uri.encode(sheetName)}")
                            },
                            onAddRow = { sheetName ->
                                navController.navigate("addRow/$sid?name=${Uri.encode(name)}&sheetName=${Uri.encode(sheetName)}")
                            },
                            shouldRefresh = shouldRefresh
                        )

                        LaunchedEffect(shouldRefresh) {
                            if (shouldRefresh) savedStateHandle["refresh"] = false
                        }
                    }

                    composable(
                        route = "edit/{spreadsheetId}/{rowIndex}?name={name}&sheetName={sheetName}",
                        arguments = listOf(
                            navArgument("spreadsheetId") { type = NavType.StringType },
                            navArgument("rowIndex") { type = NavType.IntType },
                            navArgument("name") { type = NavType.StringType; defaultValue = "Spreadsheet" },
                            navArgument("sheetName") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { editEntry ->
                        val sid = editEntry.arguments?.getString("spreadsheetId") ?: ""
                        val rowIdx = editEntry.arguments?.getInt("rowIndex") ?: 0
                        val name = editEntry.arguments?.getString("name") ?: "Spreadsheet"
                        val sheetName = editEntry.arguments?.getString("sheetName") ?: ""

                        EditRowScreen(
                            onBack = { navController.popBackStack() },
                            onSaved = {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle?.set("refresh", true)
                                navController.popBackStack()
                            },
                            onDeleted = {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle?.set("refresh", true)
                                navController.popBackStack()
                            },
                            onClone = {
                                navController.navigate("addRow/$sid?name=${Uri.encode(name)}&sheetName=${Uri.encode(sheetName)}&cloneRow=$rowIdx")
                            }
                        )
                    }

                    composable(Route.SETTINGS) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onCompanyProfile = { navController.navigate(Route.COMPANY_PROFILE) }
                        )
                    }
                    composable(Route.COMPANY_PROFILE) {
                        CompanyProfileScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = "addRow/{spreadsheetId}?name={name}&sheetName={sheetName}&cloneRow={cloneRow}",
                        arguments = listOf(
                            navArgument("spreadsheetId") { type = NavType.StringType },
                            navArgument("name") { type = NavType.StringType; defaultValue = "Spreadsheet" },
                            navArgument("sheetName") { type = NavType.StringType; defaultValue = "" },
                            navArgument("cloneRow") { type = NavType.IntType; defaultValue = -1 }
                        )
                    ) {
                        AddRowScreen(
                            onBack = { navController.popBackStack() },
                            onSaved = {
                                navController.previousBackStackEntry
                                    ?.savedStateHandle?.set("refresh", true)
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        authManager.consentLauncher = null
    }
}

private object Route {
    const val LOGIN = "login"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val COMPANY_PROFILE = "companyProfile"
}
