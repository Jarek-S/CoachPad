package pl.net.orlik.presentation.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import pl.net.orlik.R

/** Darkens the photo so the clock and buttons stay legible (and the AMOLED screen mostly dark). */
private val ScrimColor = Color.Black.copy(alpha = 0.5f)

/**
 * The night stadium shared with the platform (docs section 14), cut square for the round watch
 * screen. Each screen has its own, so swiping back shows the previous screen with its background.
 */
@Composable
fun StadiumBackground(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            painter = painterResource(R.drawable.stadium_night),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(Modifier.matchParentSize().background(ScrimColor))
        content()
    }
}
