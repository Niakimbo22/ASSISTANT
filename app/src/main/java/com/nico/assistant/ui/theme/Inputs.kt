package com.nico.assistant.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

private val FieldShape = RoundedCornerShape(NicoRadius.Field)

/**
 * Boîte commune à tous les champs : texte libre, liste déroulante, choix d'app. C'est ce qui
 * garantit que tous les champs de l'app ont exactement la même forme.
 */
@Composable
private fun FieldBox(
    focused: Boolean,
    isError: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val borderBrush: Brush = when {
        isError -> SolidColor(NicoColors.RedBright.copy(alpha = 0.8f))
        focused -> GlassBrushes.SpecularBorderStrong
        else -> GlassBrushes.SpecularBorder
    }
    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> NicoColors.GlassFillSubtle
            focused -> NicoColors.GlassFillRaised
            else -> NicoColors.GlassFill
        },
        label = "remplissage du champ"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(FieldShape)
            .background(fill, FieldShape)
            .border(1.dp, borderBrush, FieldShape),
        contentAlignment = Alignment.CenterStart
    ) {
        content()
    }
}

/** Libellé au-dessus d'un champ : jamais flottant, toujours lisible. */
@Composable
fun FieldLabel(text: String, required: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(start = NicoSpacing.xxs, bottom = 6.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = NicoColors.TextSecondary
        )
        if (required) {
            Text(" *", style = MaterialTheme.typography.bodySmall, color = NicoColors.RedBright)
        }
    }
}

@Composable
private fun SupportingText(text: String?, isError: Boolean) {
    AnimatedVisibility(
        visible = !text.isNullOrBlank(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Text(
            text = text.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) NicoColors.RedBright else NicoColors.TextTertiary,
            modifier = Modifier.padding(start = NicoSpacing.xxs, top = 6.dp)
        )
    }
}

/**
 * Champ de saisie en verre. Libellé au-dessus, texte d'aide ou d'erreur en dessous, en ligne
 * et discret.
 */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    required: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    fieldModifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) FieldLabel(label, required)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 6,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = NicoColors.TextPrimary),
            cursorBrush = SolidColor(NicoColors.RedBright),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            modifier = fieldModifier.fillMaxWidth(),
            decorationBox = { inner ->
                FieldBox(focused = focused, isError = isError, enabled = enabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            start = if (leadingIcon != null) NicoSpacing.sm else NicoSpacing.md,
                            end = if (trailing != null) NicoSpacing.xxs else NicoSpacing.md
                        )
                    ) {
                        if (leadingIcon != null) {
                            Icon(
                                leadingIcon,
                                contentDescription = null,
                                tint = NicoColors.TextTertiary,
                                modifier = Modifier.padding(end = NicoSpacing.sm).size(20.dp)
                            )
                        }
                        Box(
                            modifier = Modifier.weight(1f).padding(vertical = NicoSpacing.md),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = NicoColors.TextTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                        trailing?.invoke()
                    }
                }
            }
        )
        SupportingText(supportingText, isError)
    }
}

/**
 * Champ de choix : **même boîte** que [GlassTextField], avec un chevron. Le choix lui-même
 * s'ouvre dans une [GlassOptionSheet] — grosses cibles, verre, cohérent partout.
 */
@Composable
fun GlassSelectField(
    label: String?,
    display: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    placeholder: Boolean = false,
    supportingText: String? = null,
    required: Boolean = false,
    sheetTitle: String? = label
) {
    var open by remember { mutableStateOf(false) }
    GlassPickerField(
        label = label,
        display = display,
        onClick = { open = true },
        modifier = modifier,
        leadingIcon = leadingIcon,
        placeholder = placeholder,
        supportingText = supportingText,
        required = required
    )
    if (open) {
        GlassOptionSheet(
            title = sheetTitle,
            options = options,
            selected = selected,
            onPick = {
                open = false
                onPick(it)
            },
            onDismiss = { open = false }
        )
    }
}

/** Champ qui ouvre un sélecteur (liste, apps installées…) au lieu d'accepter du texte. */
@Composable
fun GlassPickerField(
    label: String?,
    display: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector = Symbols.ExpandMore,
    placeholder: Boolean = false,
    supportingText: String? = null,
    required: Boolean = false
) {
    val haptics = rememberHaptics()
    Column(modifier = modifier.fillMaxWidth()) {
        if (label != null) FieldLabel(label, required)
        FieldBox(
            focused = false,
            isError = false,
            enabled = true,
            modifier = Modifier.clip(FieldShape).clickable(role = Role.Button) {
                haptics.tick()
                onClick()
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = NicoSpacing.md, vertical = NicoSpacing.md)
            ) {
                if (leadingIcon != null) {
                    Icon(
                        leadingIcon,
                        contentDescription = null,
                        tint = NicoColors.TextSecondary,
                        modifier = Modifier.padding(end = NicoSpacing.sm).size(20.dp)
                    )
                }
                Text(
                    text = display,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (placeholder) NicoColors.TextTertiary else NicoColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    trailingIcon,
                    contentDescription = null,
                    tint = NicoColors.TextTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        SupportingText(supportingText, isError = false)
    }
}

/**
 * Interrupteur en verre : piste translucide, pastille blanche sur ressort, rouge Nothing
 * quand il est actif. Zone tactile de 48 dp.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = NicoMotion.bouncy(),
        label = "pastille"
    )
    val track by animateColorAsState(
        targetValue = if (checked) NicoColors.NothingRed else NicoColors.GlassFillRaised,
        animationSpec = NicoMotion.gentle(),
        label = "piste"
    )
    val thumb by animateColorAsState(
        targetValue = if (checked) Color.White else Color.White.copy(alpha = 0.75f),
        label = "pastille"
    )

    Box(
        modifier = modifier
            .size(width = 56.dp, height = NicoSpacing.touchTarget)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.toggle(it)
                    onCheckedChange(it)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 50.dp, height = 30.dp)
                .pressScale(interaction, pressedScale = 0.94f)
                .clip(CircleShape)
                .background(if (enabled) track else NicoColors.GlassFillSubtle, CircleShape)
                .background(GlassBrushes.Sheen, CircleShape)
                .border(1.dp, GlassBrushes.SpecularBorder, CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .size(26.dp)
                    .drawBehind {
                        // Petit halo sous la pastille : elle semble posée sur le verre.
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.25f),
                            radius = size.minDimension / 2f + 1.5f,
                            center = center + Offset(0f, 1.5f)
                        )
                    }
                    .background(if (enabled) thumb else NicoColors.TextDisabled, CircleShape)
            )
        }
    }
}

/** Ligne titre + description + interrupteur, toute la ligne est cliquable. */
@Composable
fun GlassToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val haptics = rememberHaptics()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(NicoRadius.Field))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    haptics.toggle(it)
                    onCheckedChange(it)
                }
            )
            .padding(vertical = NicoSpacing.xxs)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = NicoColors.TextSecondary,
                modifier = Modifier.padding(end = NicoSpacing.sm).size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = NicoColors.TextSecondary
                )
            }
        }
        // L'interrupteur reflète l'état ; la ligne entière porte le clic et la sémantique.
        GlassSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.padding(start = NicoSpacing.sm)
        )
    }
}

/** Curseur : piste de verre, remplissage rouge, valeur en mono à droite du libellé. */
@Composable
fun GlassSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    valueText: String = "%.2f".format(value)
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = NicoColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = NicoColors.TextPrimary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = NicoColors.NothingRed,
                inactiveTrackColor = NicoColors.GlassFillRaised,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
    }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun GlassInputsPreview() {
    GlassPreview {
        GlassTextField(value = "Bonne nuit", onValueChange = {}, label = "Nom", required = true)
        GlassTextField(
            value = "",
            onValueChange = {},
            placeholder = "Rechercher",
            leadingIcon = Symbols.Search
        )
        GlassTextField(
            value = "",
            onValueChange = {},
            label = "Délai avant (ms)",
            supportingText = "Obligatoire",
            isError = true
        )
        GlassPickerField(label = "Correspondance", display = "Flexible (recommandé)", onClick = {})
        GlassToggleRow(title = "Activée", checked = true, onCheckedChange = {}, description = "Répond à la voix")
        Row(horizontalArrangement = Arrangement.spacedBy(NicoSpacing.sm)) {
            GlassSwitch(checked = true, onCheckedChange = {})
            GlassSwitch(checked = false, onCheckedChange = {})
            GlassSwitch(checked = false, onCheckedChange = {}, enabled = false)
        }
        GlassSlider(label = "Confiance", value = 0.82f, onValueChange = {}, valueRange = 0.5f..0.95f)
    }
}
