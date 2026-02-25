package com.rsb.sheetsui.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rsb.sheetsui.domain.model.SummaryStats

private val CARD_SHAPE = RoundedCornerShape(20.dp)
private val ICON_BUTTON_SHAPE = RoundedCornerShape(12.dp)

@Composable
fun SummaryHeader(
    stats: SummaryStats,
    modifier: Modifier = Modifier,
    financialSumOverride: Double? = null,
    currencySymbol: String? = null,
    onBarcodeScan: (() -> Unit)? = null,
    onReceiptScan: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.List,
                label = "Records",
                value = formatRecordCount(stats.totalRecords),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.primary
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.MonetizationOn,
                label = "Financial",
                value = formatFinancial(financialSumOverride ?: stats.financialSum, currencySymbol ?: ""),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconTint = MaterialTheme.colorScheme.secondary
            )
            if (stats.statusDistribution.isNotEmpty()) {
                StatusCard(
                    modifier = Modifier.weight(1f),
                    stats = stats
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            onBarcodeScan?.let { onScan ->
                FilledIconButton(
                    onClick = onScan,
                    modifier = Modifier.size(44.dp),
                    shape = ICON_BUTTON_SHAPE
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode", modifier = Modifier.size(22.dp))
                }
            }
            onReceiptScan?.let { onScan ->
                FilledIconButton(
                    onClick = onScan,
                    modifier = Modifier.size(44.dp),
                    shape = ICON_BUTTON_SHAPE
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = "Scan receipt", modifier = Modifier.size(22.dp))
                }
            }
            onSettingsClick?.let { onSettings ->
                FilledIconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(44.dp),
                    shape = ICON_BUTTON_SHAPE
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color
) {
    ElevatedCard(
        modifier = modifier,
        shape = CARD_SHAPE,
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = containerColor),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusCard(
    modifier: Modifier = Modifier,
    stats: SummaryStats
) {
    ElevatedCard(
        modifier = modifier,
        shape = CARD_SHAPE,
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = androidx.compose.material3.CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Assignment,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                stats.statusDistribution.take(3).forEach { sc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            sc.label,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        LinearProgressIndicator(
                            progress = { sc.ratio },
                            modifier = Modifier
                                .width(36.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${sc.count}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

private fun formatRecordCount(count: Int): String = when {
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    else -> java.text.DecimalFormat("#,##0").format(count)
}

private fun formatFinancial(sum: Double?, symbol: String): String {
    if (sum == null) return "—"
    val fmt = java.text.DecimalFormat("#,##0.00")
    return (symbol + fmt.format(sum)).trim()
}
