package az.pixclean.ui.components

import androidx.compose.foundation.background
import az.pixclean.ui.theme.Sizes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import az.pixclean.ui.theme.Radius
import az.pixclean.ui.theme.Space
import coil3.compose.AsyncImage
import java.util.Locale

fun humanBytes(n: Long): String {
    if (n <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = n.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return if (i == 0) "${n} B" else String.format(Locale.US, "%.1f %s", v, units[i])
}

@Composable
fun PhotoThumb(
    model: Any?,
    modifier: Modifier = Modifier,
    corner: Dp = Radius.md,
    ring: Color? = null,
    ringWidth: Dp = 2.dp,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (ring != null) Modifier.border(ringWidth, ring, RoundedCornerShape(corner)) else Modifier)
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * A round crop of one face taken straight from the original photo. Boxes are stored square
 * and normalised, so the whole thing is two multiplications and no extra decoding pass.
 */
@Composable
fun FaceThumb(
    model: Any?,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Color? = null,
) {
    val norm = 10_000f
    val fw = ((right - left) / norm).coerceAtLeast(0.02f)
    val fh = ((bottom - top) / norm).coerceAtLeast(0.02f)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (ring != null) Modifier.border(2.dp, ring, CircleShape) else Modifier)
    ) {
        val imageW = size / fw
        val imageH = size / fh
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .size(imageW, imageH)
                .offset(x = -(imageW * (left / norm)), y = -(imageH * (top / norm))),
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

@Composable
fun Pill(
    text: String,
    color: Color,
    container: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(container)
            .padding(horizontal = Space.sm, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Space.lg, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.invoke()
    }
}

val ScreenPadding = PaddingValues(horizontal = Space.lg)

/**
 * Selection state drawn straight onto the photo. A real Checkbox reserves a 48dp touch
 * target and swamps a 96dp tile, and the tile itself already toggles selection — so this
 * is an indicator sized to be read, sitting on a scrim so it survives any photo under it.
 */
@Composable
fun TileCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(Space.xs)
            .size(26.dp)
            .clip(CircleShape)
            .background(if (checked) MaterialTheme.colorScheme.error else ScrimStrong)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (checked) Icons.Rounded.Check else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (checked) "Seçilib" else "Seçilməyib",
            tint = Color.White,
            modifier = Modifier.size(if (checked) 16.dp else 18.dp),
        )
    }
}

/** Small action that sits over a photo. */
@Composable
fun TileButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(26.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(ScrimStrong)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(15.dp))
    }
}

/** Dark enough that white sits well above AA over any photo underneath. */
val ScrimStrong = Color(0xCC101014)
