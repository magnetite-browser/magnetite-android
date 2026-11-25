package org.magnetitebrowser.android.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import org.magnetitebrowser.android.R
import uniffi.magnetite_mobile_core.Space

/**
 * A space's icon as the desktop draws it: one of its glyphs in the space's
 * colour, as a Material Symbol. The desktop stores a glyph's name, or an
 * imported icon name or emoji that maps onto one (`Glyphs.tsx` in
 * magnetite); another emoji shows as it is, no icon as the title's first
 * letter. Says nothing to TalkBack: the space's title goes with it.
 */
@Composable
fun SpaceIcon(space: Space, size: Dp, modifier: Modifier = Modifier) {
    val tint = space.tint ?: MaterialTheme.colorScheme.primary
    val icon = space.icon
    val glyph = glyph(icon)
    // Text sized to the icon, whatever the font scale.
    val fontSize = with(LocalDensity.current) { (size * 0.8f).toSp() }
    Box(modifier.size(size).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        when {
            glyph != null -> Icon(painterResource(glyph), null, Modifier.size(size), tint = tint)
            icon != null && icon.codePoints().anyMatch { it > 0x238C } -> Text(icon, fontSize = fontSize * 0.9f)
            else -> Text(
                space.title.firstOrNull()?.uppercase() ?: "•",
                color = tint, fontSize = fontSize, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** The drawable in place of the desktop glyph `icon` names. */
@DrawableRes
private fun glyph(icon: String?): Int? {
    icon ?: return null
    val lower = icon.lowercase()
    val name = when {
        icon in glyphs -> icon
        lower in aliases -> aliases[lower]
        lower in glyphs -> lower
        else -> emoji[icon]
    }
    return glyphs[name]
}

private val glyphs = mapOf(
    "bolt" to R.drawable.ic_space_bolt, "home" to R.drawable.ic_space_home,
    "briefcase" to R.drawable.ic_space_briefcase, "book" to R.drawable.ic_space_book,
    "code" to R.drawable.ic_space_code, "globe" to R.drawable.ic_space_globe,
    "heart" to R.drawable.ic_space_heart, "star" to R.drawable.ic_space_star,
    "music" to R.drawable.ic_space_music, "cart" to R.drawable.ic_space_cart,
    "camera" to R.drawable.ic_space_camera, "flask" to R.drawable.ic_space_flask,
    "leaf" to R.drawable.ic_space_leaf, "flame" to R.drawable.ic_space_flame,
    "chat" to R.drawable.ic_space_chat, "wrench" to R.drawable.ic_space_wrench,
    "compass" to R.drawable.ic_space_compass, "folder" to R.drawable.ic_space_folder,
    "plane" to R.drawable.ic_space_plane, "gamepad" to R.drawable.ic_space_gamepad,
    "shield" to R.drawable.ic_space_shield, "medical" to R.drawable.ic_space_medical,
    "github" to R.drawable.ic_space_github, "diamond" to R.drawable.ic_space_diamond,
    "circle" to R.drawable.ic_space_circle, "square" to R.drawable.ic_space_square,
    "triangle" to R.drawable.ic_space_triangle,
)

private val aliases = mapOf(
    "flash" to "bolt", "work" to "briefcase", "terminal" to "code", "gamecontroller" to "gamepad",
    "paintbrush" to "flame", "graduationcap" to "book", "film" to "camera", "airplane" to "plane",
)

private val emoji = mapOf(
    "⚡" to "bolt", "🏠" to "home", "💼" to "briefcase", "📚" to "book", "🎨" to "flame", "🎧" to "music",
    "🛠️" to "wrench", "🧪" to "flask", "🌍" to "globe", "🛒" to "cart", "💬" to "chat", "📷" to "camera",
    "🎮" to "gamepad", "✈️" to "plane", "🌿" to "leaf", "🔥" to "flame", "💡" to "bolt", "🧭" to "compass",
    "🗂️" to "folder", "✦" to "star", "◆" to "diamond", "●" to "circle", "▲" to "triangle", "■" to "square",
    "🏥" to "medical", "🐙" to "github", "❤️" to "heart", "⭐" to "star",
)
