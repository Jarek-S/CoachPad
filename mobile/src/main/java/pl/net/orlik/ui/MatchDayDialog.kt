package pl.net.orlik.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.net.orlik.R
import pl.net.orlik.review.utcDayOf
import java.time.ZoneId

/**
 * "Data meczu": the day a planned match is for, any day before or after today (design doc,
 * section 11). Starts on [initialMillis], today when null; gives the day as UTC midnight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchDayDialog(initialMillis: Long?, confirmLabel: String, onConfirm: (utcDayMillis: Long) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = utcDayOf(initialMillis ?: System.currentTimeMillis(), ZoneId.systemDefault()),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let(onConfirm) }, enabled = picker.selectedDateMillis != null) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = picker, title = { Text(stringResource(R.string.match_day), Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}
