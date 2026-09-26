package com.nico.assistant.ui.theme

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Transitions d'écran en élément partagé : le nom d'une automatisation glisse de sa carte
 * jusqu'à la barre de l'éditeur.
 *
 * Les deux portées sont fournies par la navigation (MainActivity). Absentes — aperçus,
 * écran affiché hors navigation — le modificateur ne fait rien : aucun écran n'en dépend.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/** Clé partagée du nom d'une automatisation. */
fun automationTitleKey(automationId: String): String = "automation-title-$automationId"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementOrSelf(key: Any?): Modifier {
    val shared = LocalSharedTransitionScope.current
    val animated = LocalNavAnimatedScope.current
    if (key == null || shared == null || animated == null) return this
    return with(shared) {
        this@sharedElementOrSelf.sharedElement(
            state = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = { _, _ -> NicoMotion.gentle() }
        )
    }
}
