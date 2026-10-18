package pl.net.orlik.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.ZoneId
import pl.net.orlik.R

enum class ExportFormat { TEXT, CSV, JSON }

/**
 * Hands [export] to the system's "Udostępnij" sheet: the text as a message, CSV and JSON as a
 * file. Files go to the cache and are read by the receiving app through FileProvider, so no
 * storage permission is needed.
 */
fun shareExport(context: Context, export: MatchExport, format: ExportFormat) {
    val zone = ZoneId.systemDefault()
    val resources = context.resources
    val send = Intent(Intent.ACTION_SEND)
    when (format) {
        ExportFormat.TEXT -> {
            send.type = "text/plain"
            send.putExtra(Intent.EXTRA_TEXT, shareTextOf(export, shareTextLabelsOf(resources), zone))
        }
        ExportFormat.CSV -> send.attach(context, export, zone, "csv", "text/csv") {
            matchCsvOf(export, csvLabelsOf(resources), zone)
        }
        ExportFormat.JSON -> send.attach(context, export, zone, "json", "application/json") {
            platformJsonOf(export, zone)
        }
    }
    context.startActivity(Intent.createChooser(send, resources.getString(R.string.share)))
}

private fun Intent.attach(
    context: Context,
    export: MatchExport,
    zone: ZoneId,
    extension: String,
    mimeType: String,
    content: () -> String,
) {
    val name = exportFileName(
        export,
        export.homeTeam ?: context.getString(R.string.team_home),
        export.awayTeam ?: context.getString(R.string.team_away),
        extension,
        zone,
    )
    // Only the latest export is kept: the receiving app has read the earlier ones by now
    val dir = File(context.cacheDir, "exports").apply { deleteRecursively(); mkdirs() }
    val file = File(dir, name).apply { writeText(content(), Charsets.UTF_8) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
    type = mimeType
    putExtra(Intent.EXTRA_STREAM, uri)
    putExtra(Intent.EXTRA_SUBJECT, name)
    // ClipData carries the read permission through the chooser to the chosen app
    clipData = ClipData.newRawUri(name, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
