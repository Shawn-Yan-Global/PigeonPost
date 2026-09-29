package com.octopus.ui.spacing

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared spacing and sizing scale.
 *
 * Screens used to hardcode values such as `16.dp` in more than 250 places, which made it impossible
 * to adjust the rhythm of the app in one place. Anything that repeats across screens should come
 * from here so the whole app keeps the same visual language.
 *
 * The scale follows the usual 4dp grid: `extraSmall` 4, `small` 8, `medium` 12, `large` 16,
 * `extraLarge` 20, `xxLarge` 24, `xxxLarge` 32.
 */
object AppSpacing {
    /** 1dp - hairline separation such as a divider. */
    val hairline: Dp = 1.dp

    /** 2dp - tight separation, e.g. a thin bar or a small gap inside a control. */
    val tiny: Dp = 2.dp

    /** 4dp - separation inside a single control. */
    val extraSmall: Dp = 4.dp

    /** 8dp - gap between tightly related elements, most used value. */
    val small: Dp = 8.dp

    /** 10dp - gap that needs a little more room than [small]. */
    val smallPlus: Dp = 10.dp

    /** 12dp - gap inside a group of related elements. */
    val medium: Dp = 12.dp

    /** 16dp - standard card padding and gap between cards. */
    val large: Dp = 16.dp

    /** 18dp - compact icon size, e.g. a small badge. */
    val iconSizeSmall: Dp = 18.dp

    /** 20dp - slightly more breathing room than [large]. */
    val extraLarge: Dp = 20.dp

    /** 24dp - separation between distinct sections. */
    val xxLarge: Dp = 24.dp

    /** 32dp - separation between major sections. */
    val xxxLarge: Dp = 32.dp

    /** 48dp - minimum touch target, as recommended by the Material accessibility guidelines. */
    val minTouchTarget: Dp = 48.dp

    /** 64dp - size for prominent indicators such as the service status icon. */
    val heroIconSize: Dp = 64.dp

    /** Corner radius for very compact elements such as a progress bar. */
    val tinyCornerRadius: Dp = 2.dp

    /** Width of a stroked outline, e.g. on a circular progress indicator. */
    val strokeWidth: Dp = 2.dp
}
