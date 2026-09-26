package com.nico.assistant.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nico.assistant.ui.theme.BackButton
import com.nico.assistant.ui.theme.GlassButton
import com.nico.assistant.ui.theme.GlassButtonStyle
import com.nico.assistant.ui.theme.GlassCard
import com.nico.assistant.ui.theme.GlassDialog
import com.nico.assistant.ui.theme.GlassIconButton
import com.nico.assistant.ui.theme.GlassScaffold
import com.nico.assistant.ui.theme.GlassTopBar
import com.nico.assistant.ui.theme.LargeTitle
import com.nico.assistant.ui.theme.NicoColors
import com.nico.assistant.ui.theme.NicoSpacing
import com.nico.assistant.ui.theme.StatusBadge
import com.nico.assistant.ui.theme.Symbols

/**
 * Journal d'exécution (spec §8 et §11).
 *
 * Il évite d'avoir besoin de Logcat dans la majorité des cas — ce qui compte quand on
 * débogue sans câble.
 */
@Composable
fun LogsScreen(viewModel: LogsViewModel, onBack: () -> Unit) {
    val rows by viewModel.rows.collectAsState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var confirmClear by remember { mutableStateOf(false) }
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8 }
    }
    val titleCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > with(density) { 44.dp.toPx() }
        }
    }

    GlassScaffold(
        topBar = {
            GlassTopBar(
                title = "Journal",
                scrolled = scrolled,
                showTitle = titleCollapsed,
                navigationIcon = { BackButton(onBack) },
                actions = {
                    GlassIconButton(
                        icon = Symbols.Delete,
                        contentDescription = "Vider le journal",
                        onClick = { confirmClear = true },
                        enabled = rows.isNotEmpty()
                    )
                }
            )
        },
        overlay = {
            if (confirmClear) {
                GlassDialog(
                    onDismissRequest = { confirmClear = false },
                    title = "Vider le journal ?",
                    buttons = {
                        GlassButton(
                            text = "Vider",
                            onClick = {
                                viewModel.clear()
                                confirmClear = false
                            },
                            style = GlassButtonStyle.Danger,
                            icon = Symbols.Delete,
                            modifier = Modifier.fillMaxWidth()
                        )
                        GlassButton(
                            text = "Annuler",
                            onClick = { confirmClear = false },
                            style = GlassButtonStyle.Ghost,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                ) {
                    Text(
                        "Les ${rows.size} entrées seront effacées. Les automatisations ne sont pas touchées.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NicoColors.TextSecondary
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + NicoSpacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(NicoSpacing.xs)
        ) {
            item(key = "title") {
                val failures = rows.count { !it.log.success }
                LargeTitle(
                    "Journal",
                    subtitle = if (rows.isEmpty()) "AUCUNE ENTRÉE" else "${rows.size} ENTRÉES · $failures ÉCHECS"
                )
            }

            if (rows.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(NicoSpacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(NicoSpacing.xs)
                    ) {
                        Icon(Symbols.History, contentDescription = null, tint = NicoColors.TextTertiary, modifier = Modifier.size(36.dp))
                        Text("Rien à afficher pour l'instant", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Chaque phrase entendue apparaîtra ici, reconnue ou non.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NicoColors.TextSecondary
                        )
                    }
                }
            }

            items(rows, key = { it.log.id }) { row ->
                LogCard(row, modifier = Modifier.animateItem().padding(horizontal = NicoSpacing.gutter))
            }
        }
    }
}

@Composable
private fun LogCard(row: LogRow, modifier: Modifier = Modifier) {
    val success = row.log.success
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = NicoSpacing.md, vertical = NicoSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (success) Symbols.CheckCircleFilled else Symbols.ErrorFilled,
                contentDescription = if (success) "Réussi" else "Échec",
                tint = if (success) NicoColors.Success else NicoColors.RedBright,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = NicoSpacing.xs)
            )
            Text(row.time, style = MaterialTheme.typography.labelMedium, color = NicoColors.TextTertiary)
        }
        if (row.log.heardText.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 28.dp)) {
                Text(
                    text = "« ${row.log.heardText} »",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NicoColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge("score ${row.score}", modifier = Modifier.padding(start = NicoSpacing.xs))
            }
        }
        row.log.errorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = NicoColors.RedBright,
                modifier = Modifier.padding(start = 28.dp)
            )
        }
    }
}
