package az.pixclean.ui.theme

import androidx.compose.ui.unit.dp

/**
 * One spacing scale for the whole app. Anything between two steps is a decision that
 * needs a reason, so it should be added here rather than typed inline.
 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

object Radius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 18.dp
    val pill = 999.dp
}

object Sizes {
    /** Android's minimum comfortable touch target. */
    val touchTarget = 48.dp
    val thumbSmall = 64.dp
    val thumbGrid = 112.dp
    val faceCover = 96.dp
    val strokeThin = 1.dp
    val strokeThick = 2.dp
}
