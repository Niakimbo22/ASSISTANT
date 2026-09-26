package com.nico.assistant.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nico.assistant.shizuku.ShizukuState
import com.nico.assistant.ui.theme.ActionIconBadge
import com.nico.assistant.ui.theme.BackButton
import com.nico.assistant.ui.theme.GlassButton
import com.nico.assistant.ui.theme.GlassButtonStyle
import com.nico.assistant.ui.theme.GlassCard
import com.nico.assistant.ui.theme.GlassScaffold
import com.nico.assistant.ui.theme.GlassSlider
import com.nico.assistant.ui.theme.GlassTopBar
import com.nico.assistant.ui.theme.LargeTitle
import com.nico.assistant.ui.theme.NicoColors
import com.nico.assistant.ui.theme.NicoSpacing
import com.nico.assistant.ui.theme.SectionLabel
import com.nico.assistant.ui.theme.StatusBadge
import com.nico.assistant.ui.theme.Symbols

/**
 * Réglages système (spec §6.4) : le guide Shizuku s'adapte à l'état courant, et le service
 * d'accessibilité a son propre raccourci.
 */
@Composable
fun SystemSettingsScreen(
    viewModel: SystemSettingsViewModel,
    onBack: () -> Unit,
    onOpenLegacySettings: () -> Unit,
    onOpenLogs: () -> Unit
) {
    val state by viewModel.shizukuState.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val thresholds by viewModel.thresholds.collectAsState()
    val transferMessage by viewModel.transferMessage.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::import) }

    // L'état peut avoir changé pendant qu'on était ailleurs (Shizuku relancé, autorisation
    // accordée depuis une autre app).
    LaunchedEffect(Unit) { viewModel.refresh() }

    GlassScaffold(
        topBar = {
            GlassTopBar(
                title = "Réglages",
                scrolled = scrollState.value > 8,
                showTitle = scrollState.value > 120,
                navigationIcon = { BackButton(onBack) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + NicoSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(NicoSpacing.md)
        ) {
            LargeTitle("Réglages", subtitle = "SYSTÈME · VOIX · SAUVEGARDE")

            Column(
                modifier = Modifier.padding(horizontal = NicoSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(NicoSpacing.md)
            ) {
                SettingsCard(
                    icon = Symbols.ShieldFilled,
                    accent = if (state.isReady) NicoColors.Success else NicoColors.Warning,
                    title = "Shizuku",
                    badge = {
                        StatusBadge(
                            state.title,
                            color = if (state.isReady) NicoColors.Success else NicoColors.Warning
                        )
                    }
                ) {
                    Text(state.advice, style = MaterialTheme.typography.bodyMedium, color = NicoColors.TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(NicoSpacing.xs)) {
                        when (state) {
                            ShizukuState.PERMISSION_NEEDED, ShizukuState.PERMISSION_DENIED ->
                                GlassButton("Autoriser", onClick = viewModel::requestPermission, icon = Symbols.LockFilled, height = 48.dp)

                            ShizukuState.READY ->
                                GlassButton("Tester", onClick = viewModel::test, icon = Symbols.ScienceFilled, height = 48.dp)

                            else -> Unit
                        }
                        GlassButton(
                            "Actualiser",
                            onClick = viewModel::refresh,
                            icon = Symbols.Sync,
                            style = GlassButtonStyle.Secondary,
                            height = 48.dp
                        )
                    }
                    AnimatedVisibility(
                        visible = testResult != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = testResult.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = NicoColors.TextPrimary
                        )
                    }
                    Text(
                        text = "Sans Shizuku, les actions système se rabattent sur l'écran de " +
                            "réglages correspondant : rien ne casse, ça demande juste un tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NicoColors.TextTertiary
                    )
                }

                val accessibilityOn = viewModel.accessibilityEnabled
                SettingsCard(
                    icon = Symbols.AccessibilityNew,
                    accent = if (accessibilityOn) NicoColors.Success else NicoColors.TextSecondary,
                    title = "Service d'accessibilité",
                    badge = {
                        StatusBadge(
                            if (accessibilityOn) "Actif" else "Inactif",
                            color = if (accessibilityOn) NicoColors.Success else NicoColors.TextSecondary
                        )
                    }
                ) {
                    Text(
                        text = if (accessibilityOn) {
                            "L'action « Piloter l'app musique » peut fonctionner."
                        } else {
                            "L'action « Piloter l'app musique » échouera proprement tant qu'il est coupé."
                        },
                        style = MaterialTheme.typography.bodyMedium,
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

                SettingsCard(
                    icon = Symbols.GraphicEq,
                    accent = NicoColors.RedBright,
                    title = "Seuils de reconnaissance"
                ) {
                    Text(
                        text = "Plus le seuil de confiance est bas, plus l'app se lance sans " +
                            "demander — et plus elle se trompe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NicoColors.TextSecondary
                    )
                    GlassSlider(
                        label = "Confiance",
                        value = thresholds.confident,
                        valueRange = 0.5f..0.95f,
                        onValueChange = viewModel::setConfident
                    )
                    GlassSlider(
                        label = "Plancher (en dessous : j'ai pas compris)",
                        value = thresholds.ambiguousFloor,
                        valueRange = 0.1f..0.7f,
                        onValueChange = viewModel::setAmbiguousFloor
                    )
                    GlassSlider(
                        label = "Écart minimum entre deux candidates",
                        value = thresholds.minimumGap,
                        valueRange = 0.05f..0.4f,
                        onValueChange = viewModel::setMinimumGap
                    )
                    GlassButton(
                        text = "Revenir aux valeurs par défaut",
                        onClick = viewModel::resetThresholds,
                        icon = Symbols.RestartAlt,
                        style = GlassButtonStyle.Ghost,
                        height = 44.dp
                    )
                }

                SettingsCard(
                    icon = Symbols.Download,
                    accent = NicoColors.Condition,
                    title = "Sauvegarde"
                ) {
                    Text(
                        text = "Exporte tes automatisations en JSON pour les garder, les " +
                            "partager, ou les remettre après une réinstallation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NicoColors.TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(NicoSpacing.xs)) {
                        GlassButton(
                            text = "Exporter",
                            onClick = { exportLauncher.launch("nicoassistant-automatisations.json") },
                            icon = Symbols.Upload,
                            style = GlassButtonStyle.Secondary,
                            height = 48.dp,
                            modifier = Modifier.weight(1f)
                        )
                        GlassButton(
                            text = "Importer",
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                            icon = Symbols.Download,
                            style = GlassButtonStyle.Secondary,
                            height = 48.dp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    AnimatedVisibility(
                        visible = transferMessage != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = transferMessage.orEmpty(),
                            style = MaterialTheme.typography.labelMedium,
                            color = NicoColors.TextPrimary
                        )
                    }
                }

                SectionLabel("Plus", modifier = Modifier.padding(top = NicoSpacing.xs, start = NicoSpacing.xxs))

                NavigationRow(
                    icon = Symbols.History,
                    title = "Journal d'exécution",
                    subtitle = "Chaque tentative, reconnue ou non, avec son score",
                    onClick = onOpenLogs
                )
                NavigationRow(
                    icon = Symbols.MusicNoteFilled,
                    title = "Assistant",
                    subtitle = "App musique, écoute automatique à l'ouverture",
                    onClick = onOpenLegacySettings
                )
            }
        }
    }
}

/** Carte de réglage : icône colorée, titre, badge d'état, puis contenu. */
@Composable
private fun SettingsCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(NicoSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionIconBadge(icon = icon, color = accent, size = 36.dp)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(horizontal = NicoSpacing.sm)
            )
            if (badge != null) badge()
        }
        content()
    }
}

@Composable
private fun NavigationRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(NicoSpacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActionIconBadge(icon = icon, color = NicoColors.TextSecondary, size = 36.dp)
            Column(modifier = Modifier.weight(1f).padding(horizontal = NicoSpacing.sm)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NicoColors.TextSecondary)
            }
            Icon(Symbols.ChevronRight, contentDescription = null, tint = NicoColors.TextTertiary, modifier = Modifier.size(22.dp))
        }
    }
}
