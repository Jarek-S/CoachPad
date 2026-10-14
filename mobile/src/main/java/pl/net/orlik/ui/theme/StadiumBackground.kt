package pl.net.orlik.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import pl.net.orlik.R

/** Text and icons drawn straight on the stadium (top bars, section headers); cards stay white. */
val OnStadium = Color.White
val OnStadiumMuted = Color(0xFFC5D3EA)

/**
 * The night stadium shared with the platform (docs section 14), with its blue tint. Android picks
 * the portrait or landscape image by itself (drawable-nodpi / drawable-land-nodpi).
 */
@Composable
fun StadiumBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(Color(0xFF061430))) {
        Image(
            painter = painterResource(R.drawable.stadium_night),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color(0xE6061640),
                    0.45f to Color(0x9E0A2C78),
                    1f to Color(0x8C061640),
                ),
            ),
        )
        content()
    }
}

/** Top bar over the stadium: no fill of its own, white title and icons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun stadiumTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
    titleContentColor = OnStadium,
    navigationIconContentColor = OnStadium,
    actionIconContentColor = OnStadium,
)
