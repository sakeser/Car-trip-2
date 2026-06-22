package com.cartrip.analyzer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.cartrip.analyzer.analysis.TripAnalysis
import com.cartrip.analyzer.data.TripEntity
import com.cartrip.analyzer.export.TripExcel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object TripActions {

    fun openInMaps(context: Context, lat: Double, lon: Double, label: String) {
        val q = String.format(Locale.US, "%.6f,%.6f", lat, lon)
        val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$q")
        launch(context, uri)
    }

    fun routeInMaps(
        context: Context,
        sLat: Double, sLon: Double, eLat: Double, eLon: Double
    ) {
        val o = String.format(Locale.US, "%.6f,%.6f", sLat, sLon)
        val d = String.format(Locale.US, "%.6f,%.6f", eLat, eLon)
        val uri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&origin=$o&destination=$d&travelmode=driving"
        )
        launch(context, uri)
    }

    private fun launch(context: Context, uri: Uri) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(context, "No app to open maps", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun shareExcel(context: Context, trip: TripEntity, a: TripAnalysis) {
        try {
            val file = withContext(Dispatchers.IO) { TripExcel.write(context, trip, a) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(
                    Intent.createChooser(send, "Share trip Excel")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "Could not share Excel: ${e.userMessage()}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun Throwable.userMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}
