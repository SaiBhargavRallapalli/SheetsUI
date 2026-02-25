package com.rsb.sheetsui.ui.screens

/**
 * sealed class representing navigation destinations.
 */
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object SpreadsheetList : Screen("spreadsheet_list")
    object SheetDetail : Screen("sheet_detail/{spreadsheetId}/{sheetName}") {
        fun createRoute(spreadsheetId: String, sheetName: String) =
            "sheet_detail/$spreadsheetId/$sheetName"
    }
}
