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

    /**
     * Delete exported `.xlsx` files past the [ExportRetention] policy (age + count). Runs on each export so
     * the folder self-maintains; fail-soft (a delete failure never blocks the export). Returns the count
     * removed. These files are unencrypted app-private storage, so bounding them is a privacy measure.
     */
    fun pruneOldExports(context: Context, nowMs: Long = System.currentTimeMillis()): Int {
        val files = exportDir(context).listFiles()?.filter { it.isFile && it.name.endsWith(".xlsx") }
            ?: return 0
        val entries = files.map { ExportRetention.Entry(it.name, it.lastModified()) }
        val doomed = ExportRetention.toDelete(entries, nowMs).mapTo(HashSet()) { it.name }
        var n = 0
        files.forEach { if (it.name in doomed && runCatching { it.delete() }.getOrDefault(false)) n++ }
        return n
    }

    /** Delete every exported `.xlsx` (user-initiated "clear exported files"). Returns the count removed. */
    fun clearAllExports(context: Context): Int {
        val files = exportDir(context).listFiles()?.filter { it.isFile && it.name.endsWith(".xlsx") }
            ?: return 0
        var n = 0
        files.forEach { if (runCatching { it.delete() }.getOrDefault(false)) n++ }
        return n
    }

    fun write(context: Context, trip: TripEntity, a: TripAnalysis): File {
        runCatching { pruneOldExports(context) }  // keep the export folder bounded; never block the write
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
