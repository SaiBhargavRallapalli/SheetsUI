package com.rsb.sheetsui.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SheetSettingsModal(
    headers: List<String>,
    currentFinancialColIndex: Int?,
    currentCurrencySymbol: String?,
    onDismiss: () -> Unit,
    onSave: (financialColumnIndex: Int?, currencySymbol: String?) -> Unit
) {
    var selectedFinCol by remember(currentFinancialColIndex) { mutableStateOf(currentFinancialColIndex) }
    var selectedCurrency by remember(currentCurrencySymbol) { mutableStateOf(currentCurrencySymbol ?: "") }
    val currencyOptions = listOf("" to "None", "₹" to "₹ Rupee", "$" to "$ Dollar", "£" to "£ Pound", "€" to "€ Euro", "¥" to "¥ Yen")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sheet Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Primary Financial Column",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedFinCol == null,
                        onClick = {
                            selectedFinCol = null
                            onSave(null, selectedCurrency.takeIf { it.isNotBlank() })
                        }
                    )
                    Text("Auto-detect", modifier = Modifier.padding(start = 8.dp))
                }
                headers.forEachIndexed { idx, h ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedFinCol == idx,
                            onClick = {
                                selectedFinCol = idx
                                onSave(idx, selectedCurrency.takeIf { it.isNotBlank() })
                            }
                        )
                        Text(
                            h.ifEmpty { "Column ${idx + 1}" },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Text(
                    "Currency Symbol",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
                currencyOptions.forEach { (sym, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedCurrency.isEmpty() && sym.isEmpty()) || selectedCurrency == sym,
                            onClick = {
                                selectedCurrency = sym
                                onSave(selectedFinCol, sym.takeIf { it.isNotBlank() })
                            }
                        )
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
