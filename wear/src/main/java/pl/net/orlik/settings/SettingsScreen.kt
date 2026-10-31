package pl.net.orlik.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import pl.net.orlik.R
import pl.net.orlik.presentation.theme.CoachPadTheme
import pl.net.orlik.presentation.theme.SmallLabelColor

/** Settings list; swipe right to go back. */
@Composable
fun SettingsScreen(
    settings: ShiftTimerSettings,
    onShiftTimerEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (deltaMinutes: Int) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text(stringResource(R.string.settings)) }
            }
            item {
                SwitchButton(
                    checked = settings.enabled,
                    onCheckedChange = onShiftTimerEnabledChange,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.shift_timer))
                }
            }
            item {
                IntervalStepper(
                    minutes = settings.intervalMinutes,
                    enabled = settings.enabled,
                    onChange = onIntervalChange,
                )
            }
        }
    }
}

/** − value + row; greyed out while the shift timer is off (the interval applies only when on). */
@Composable
private fun IntervalStepper(minutes: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val range = ShiftTimerSettings.INTERVAL_MINUTES_RANGE
    Column(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.shift_interval), color = SmallLabelColor, fontSize = 13.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepButton(R.drawable.ic_remove, R.string.shorter_interval, enabled && minutes > range.first) { onChange(-1) }
            Text(stringResource(R.string.minutes_value, minutes), fontSize = 22.sp)
            StepButton(R.drawable.ic_add, R.string.longer_interval, enabled && minutes < range.last) { onChange(+1) }
        }
    }
}

@Composable
private fun StepButton(@DrawableRes iconRes: Int, descriptionRes: Int, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(descriptionRes),
            modifier = Modifier.size(24.dp),
        )
    }
}

@WearPreviewDevices
@Composable
fun SettingsPreview() {
    CoachPadTheme {
        SettingsScreen(ShiftTimerSettings(enabled = true), onShiftTimerEnabledChange = {}, onIntervalChange = {})
    }
}
