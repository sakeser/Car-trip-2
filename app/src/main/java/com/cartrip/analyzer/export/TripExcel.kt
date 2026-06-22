package com.cartrip.analyzer.export

import android.content.Context
import com.cartrip.analyzer.analysis.TripAnalysis
import com.cartrip.analyzer.data.TripEntity
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes a per-trip .xlsx (Summary / Samples / Events) using FastExcel. */
object TripExcel {

    private val fileStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)

    fun exportDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "trips")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun fileFor(context: Context, trip: TripEntity): File {
        val name = "Trip_${trip.id}_${fileStamp.format(Date(trip.startTime))}.xlsx"
        return File(exportDir(context), name)
    }

    fun write(context: Context, trip: TripEntity, a: TripAnalysis): File {
        val file = fileFor(context, trip)
        FileOutputStream(file).use { os ->
            val wb = Workbook(os, "CarTripAnalyzer", "1.1")

            val summary = wb.newWorksheet("Summary")
            ExportData.SUMMARY_HEADER.forEachIndexed { c, h -> summary.value(0, c, h) }
            ExportData.summaryRow(trip, a).forEachIndexed { c, v -> put(summary, 1, c, v) }

            val samples = wb.newWorksheet("Samples")
            ExportData.SAMPLE_HEADER.forEachIndexed { c, h -> samples.value(0, c, h) }
            ExportData.sampleRows(trip.id, a, cap = Int.MAX_VALUE)
                .forEachIndexed { r, row -> row.forEachIndexed { c, v -> put(samples, r + 1, c, v) } }

            val events = wb.newWorksheet("Events")
            ExportData.EVENT_HEADER.forEachIndexed { c, h -> events.value(0, c, h) }
            ExportData.eventRows(trip.id, a)
                .forEachIndexed { r, row -> row.forEachIndexed { c, v -> put(events, r + 1, c, v) } }

            wb.finish()
        }
        return file
    }

    private fun put(ws: Worksheet, r: Int, c: Int, v: String) {
        val num = v.toDoubleOrNull()
        if (num != null) ws.value(r, c, num) else ws.value(r, c, v)
    }
}
