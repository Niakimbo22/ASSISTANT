package com.nico.assistant.ui.voice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nico.assistant.core.matching.MatchResult
import com.nico.assistant.core.pipeline.AssistantState
import com.nico.assistant.ui.theme.ActionIconBadge
import com.nico.assistant.ui.theme.GlassButton
import com.nico.assistant.ui.theme.GlassButtonStyle
import com.nico.assistant.ui.theme.GlassIconButton
import com.nico.assistant.ui.theme.NicoColors
import com.nico.assistant.ui.theme.NicoMotion
import com.nico.assistant.ui.theme.NicoSpacing
import com.nico.assistant.ui.theme.Symbols
import com.nico.assistant.ui.theme.VoiceWave
import com.nico.assistant.ui.theme.frostedGlass
import com.nico.assistant.ui.theme.rememberHaptics
import com.nico.assistant.ui.theme.visual
import kotlinx.coroutines.delay

/**
 * Pastille d'écoute flottante, façon Dynamic Island (spec §7.5, version in-app).
 *
 * Elle naît sous la barre d'état, s'élargit pendant l'écoute (onde qui suit onRmsChanged,
 * transcription en direct), puis se déploie en carte pour les choix, l'échec ou le résultat.
 * Tout ce qu'elle montre vient de [AssistantState] : elle ne sait rien des automatisations.
 *
 * À poser dans le calque supérieur de l'app : elle floute ce qui passe dessous.
 */
@Composable
fun BoxScope.ListeningIsland(
    state: AssistantState,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onChoose: (MatchResult, String) -> Unit,
    onCreateAutomation: (String) -> Unit
) {
    val haptics = rememberHaptics()

    // Un résultat réussi se range tout seul ; un échec attend qu'on le lise.
    LaunchedEffect(state) {
        when (state) {
            is AssistantState.Done -> {
                if (state.report.success) haptics.confirm() else haptics.reject()
                delay(if (state.report.success) 2_600 else 5_000)
                onStop()
            }
            is AssistantState.NotUnderstood, is AssistantState.Failed -> haptics.reject()
            is AssistantState.Choosing -> haptics.tick()
            else -> Unit
        }
    }

    // Pendant l'animation de sortie, on garde le dernier contenu affiché au lieu d'un vide.
    var shown by remember { mutableStateOf(state) }
    if (state !is AssistantState.Idle) shown = state

    AnimatedVisibility(
        visible = state !is AssistantState.Idle,
        enter = slideInVertically(NicoMotion.bouncy()) { -it } + fadeIn() + scaleIn(initialScale = 0.6f),
        exit = slideOutVertically(NicoMotion.gentle()) { -it } + fadeOut() + scaleOut(targetScale = 0.6f),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = NicoSpacing.xs, start = NicoSpacing.md, end = NicoSpacing.md)
    ) {
        val expanded = shown is AssistantState.Choosing ||
            shown is AssistantState.NotUnderstood ||
            shown is AssistantState.Failed ||
            shown is AssistantState.Done
        val shape = RoundedCornerShape(if (expanded) 30.dp else 50.dp)

        AnimatedContent(
            targetState = shown,
            contentKey = { it::class },
            transitionSpec = {
                (fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(120)))
                    .using(SizeTransform(clip = false) { _, _ -> NicoMotion.bouncy() })
            },
            label = "pastille d'écoute",
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(shape)
                .frostedGlass(shape)
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) { current ->
            when (current) {
                is AssistantState.Listening -> ListeningPill(current, onStop)
                is AssistantState.Thinking -> BusyPill(text = "« ${current.heard} »", onStop = onStop)
                is AssistantState.Running -> BusyPill(text = current.name, onStop = onStop, running = true)
                is AssistantState.Choosing -> ChoosingCard(current, onChoose, onStop)
                is AssistantState.NotUnderstood -> NotUnderstoodCard(current.heard, onRetry, onCreateAutomation, onStop)
                is AssistantState.Failed -> FailedCard(current.message, onRetry, onStop)
                is AssistantState.Done -> DoneCard(current, onStop)
                AssistantState.Idle -> Box(modifier = Modifier.size(1.dp))
            }
        }
    }
}

/** Écoute : point rouge vivant, onde, transcription qui défile en direct. */
@Composable
private fun ListeningPill(state: AssistantState.Listening, onStop: () -> Unit) {
    val blink = rememberInfiniteTransition(label = "voyant")
    val dot by blink.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "voyant d'écoute"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 56.dp)
            .padding(start = NicoSpacing.lg, end = NicoSpacing.xxs)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .graphicsLayer { alpha = dot }
                .background(NicoColors.NothingRed, CircleShape)
        )
        VoiceWave(
            level = state.level,
            color = NicoColors.TextPrimary,
            bars = 7,
            modifier = Modifier.padding(start = NicoSpacing.sm).size(width = 44.dp, height = 22.dp)
        )
        Text(
            text = state.partial.ifBlank { "Je t'écoute…" },
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.partial.isBlank()) NicoColors.TextSecondary else NicoColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = NicoSpacing.sm)
        )
        GlassIconButton(
            icon = Symbols.Close,
            contentDescription = "Arrêter l'écoute",
            onClick = onStop,
            tint = NicoColors.TextSecondary,
            iconSize = 20.dp
        )
    }
}

@Composable
private fun BusyPill(text: String, onStop: () -> Unit, running: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 56.dp)
            .padding(start = NicoSpacing.lg, end = NicoSpacing.xxs)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = if (running) NicoColors.RedBright else NicoColors.TextPrimary,
            strokeWidth = 2.dp
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(horizontal = NicoSpacing.sm)
        )
        GlassIconButton(
            icon = Symbols.Close,
            contentDescription = "Annuler",
            onClick = onStop,
            tint = NicoColors.TextSecondary,
            iconSize = 20.dp
        )
    }
}

@Composable
private fun CardFrame(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(NicoSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NicoSpacing.sm)
    ) { content() }
}

@Composable
private fun CardHeader(title: String, subtitle: String?, onClose: () -> Unit, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Column(modifier = Modifier.weight(1f).padding(horizontal = NicoSpacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = NicoColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        GlassIconButton(icon = Symbols.Close, contentDescription = "Fermer", onClick = onClose, tint = NicoColors.TextSecondary, iconSize = 20.dp)
    }
}

@Composable
private fun ChoosingCard(
    state: AssistantState.Choosing,
    onChoose: (MatchResult, String) -> Unit,
    onClose: () -> Unit
) {
    CardFrame {
        CardHeader(
            title = "Tu voulais dire ?",
            subtitle = "« ${state.heard} »",
            onClose = onClose,
            icon = { Icon(Symbols.AutoAwesomeFilled, contentDescription = null, tint = NicoColors.RedBright, modifier = Modifier.size(24.dp)) }
        )
        for (choice in state.choices) {
            val first = choice.automation.actions.firstOrNull()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onChoose(choice, state.heard) }
                    .padding(vertical = NicoSpacing.xs, horizontal = NicoSpacing.xxs)
            ) {
                if (first != null) {
                    ActionIconBadge(type = first.type, size = 36.dp)
                }
                Text(
                    text = choice.automation.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(horizontal = NicoSpacing.sm)
                )
                Text(
                    text = "${(choice.score * 100).toInt()} %",
                    style = MaterialTheme.typography.labelMedium,
                    color = NicoColors.TextTertiary
                )
            }
        }
        GlassButton(text = if (state.choices.size == 1) "Non, autre chose" else "Aucune de celles-ci", onClick = onClose, style = GlassButtonStyle.Ghost, modifier = Modifier.fillMaxWidth(), height = 44.dp)
    }
}

@Composable
private fun NotUnderstoodCard(
    heard: String,
    onRetry: () -> Unit,
    onCreate: (String) -> Unit,
    onClose: () -> Unit
) {
    CardFrame {
        CardHeader(
            title = "J'ai pas compris",
            subtitle = if (heard.isNotBlank()) "« $heard »" else null,
            onClose = onClose,
            icon = { Icon(Symbols.Info, contentDescription = null, tint = NicoColors.Warning, modifier = Modifier.size(24.dp)) }
        )
        if (heard.isNotBlank()) {
            GlassButton(
                text = "Créer une automatisation pour ça",
                onClick = { onCreate(heard) },
                icon = Symbols.Add,
                modifier = Modifier.fillMaxWidth(),
                height = 48.dp
            )
        }
        GlassButton(
            text = "Réessayer",
            onClick = onRetry,
            icon = Symbols.Mic,
            style = GlassButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
            height = 48.dp
        )
    }
}

@Composable
private fun FailedCard(message: String, onRetry: () -> Unit, onClose: () -> Unit) {
    CardFrame {
        CardHeader(
            title = "Écoute interrompue",
            subtitle = message,
            onClose = onClose,
            icon = { Icon(Symbols.ErrorFilled, contentDescription = null, tint = NicoColors.RedBright, modifier = Modifier.size(24.dp)) }
        )
        GlassButton(
            text = "Réessayer",
            onClick = onRetry,
            icon = Symbols.Mic,
            style = GlassButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
            height = 48.dp
        )
    }
}

@Composable
private fun DoneCard(state: AssistantState.Done, onClose: () -> Unit) {
    val report = state.report
    CardFrame {
        CardHeader(
            title = report.feedbackText(),
            subtitle = if (report.success) null else report.errorMessage,
            onClose = onClose,
            icon = {
                Icon(
                    imageVector = if (report.success) Symbols.CheckCircleFilled else Symbols.WarningFilled,
                    contentDescription = null,
                    tint = if (report.success) NicoColors.Success else NicoColors.Warning,
                    modifier = Modifier.size(24.dp)
                )
            }
        )
        if (report.outcomes.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                for (outcome in report.outcomes.take(6)) {
                    val visual = outcome.spec.type.visual()
                    Box(contentAlignment = Alignment.BottomEnd) {
                        ActionIconBadge(icon = visual.icon, color = visual.color, size = 32.dp, dimmed = !outcome.succeeded)
                        if (!outcome.succeeded) {
                            Icon(Symbols.ErrorFilled, contentDescription = "Échec", tint = NicoColors.RedBright, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}
