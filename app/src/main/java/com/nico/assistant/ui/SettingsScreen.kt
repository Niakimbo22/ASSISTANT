package com.nico.assistant.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nico.assistant.ui.theme.ActionIconBadge
import com.nico.assistant.ui.theme.AppIcon
import com.nico.assistant.ui.theme.BackButton
import com.nico.assistant.ui.theme.GlassButton
import com.nico.assistant.ui.theme.GlassButtonStyle
import com.nico.assistant.ui.theme.GlassCard
import com.nico.assistant.ui.theme.GlassScaffold
import com.nico.assistant.ui.theme.GlassToggleRow
import com.nico.assistant.ui.theme.GlassTopBar
import com.nico.assistant.ui.theme.LargeTitle
import com.nico.assistant.ui.theme.NicoColors
import com.nico.assistant.ui.theme.NicoSpacing
import com.nico.assistant.ui.theme.OptionRow
import com.nico.assistant.ui.theme.SectionLabel
import com.nico.assistant.ui.theme.Symbols

/**
 * Réglages de l'assistant hérités de la V1 : écoute automatique, service d'accessibilité et
 * app musique cible — dans le même langage visuel que le reste.
 */
@Composable
fun SettingsScreen(
    vm: AssistantViewModel,
    onBack: () -> Unit,
) {
    val state = vm.state
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8 }
    }

    // Charge la liste des applis à l'ouverture de l'écran.
    LaunchedEffect(Unit) { vm.loadInstalledApps() }

    GlassScaffold(
        topBar = {
            GlassTopBar(
                title = "Assistant",
                scrolled = scrolled,
                showTitle = listState.firstVisibleItemIndex > 0,
                navigationIcon = { BackButton(onBack) }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + NicoSpacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(NicoSpacing.sm)
        ) {
            item(key = "title") { LargeTitle("Assistant", subtitle = "ÉCOUTE · MUSIQUE") }

            item(key = "listen") {
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = NicoSpacing.gutter)) {
                    GlassToggleRow(
                        title = "Écouter à l'ouverture",
                        description = "Le micro démarre dès que l'app s'ouvre (idéal avec le double-appui power).",
                        icon = Symbols.MicFilled,
                        checked = state.autoListen,
                        onCheckedChange = { vm.setAutoListen(it) }
                    )
                }
            }

            item(key = "a11y") {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = NicoSpacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(NicoSpacing.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ActionIconBadge(icon = Symbols.AccessibilityNew, color = NicoColors.TextSecondary, size = 36.dp)
                        Text(
                            "Service « NicoAssistant Music »",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = NicoSpacing.sm)
                        )
                    }
                    Text(
                        "Nécessaire au repli qui pilote l'interface de l'app musique quand la recherche directe ne suffit pas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NicoColors.TextSecondary
                    )
                    GlassButton(
                        text = "Ouvrir les réglages d'accessibilité",
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        icon = Symbols.OpenInNew,
                        style = GlassButtonStyle.Secondary,
                        height = 48.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item(key = "music-header") {
                Column(
                    modifier = Modifier.padding(horizontal = NicoSpacing.gutter).padding(top = NicoSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SectionLabel("App musique", icon = Symbols.MusicNoteFilled)
                    Text(
                        text = state.musicPackageLabel?.let { "$it · ${state.musicPackage}" }
                            ?: (state.musicPackage ?: "Aucune app choisie"),
                        style = MaterialTheme.typography.labelMedium,
                        color = NicoColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Le vrai nom de paquet est affiché : utile pour une app installée à la main (RVX).",
                        style = MaterialTheme.typography.bodySmall,
                        color = NicoColors.TextTertiary
                    )
                }
            }

            // Liste des applis installées, sélectionnables.
            items(state.installedApps, key = { it.packageName }) { app ->
                OptionRow(
                    label = app.label,
                    description = app.packageName,
                    selected = app.packageName == state.musicPackage,
                    onClick = { vm.selectMusicApp(app) },
                    leading = { AppIcon(app.packageName) },
                    modifier = Modifier.padding(horizontal = NicoSpacing.md)
                )
            }
        }
    }
}
