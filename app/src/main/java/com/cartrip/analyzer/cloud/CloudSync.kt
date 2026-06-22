package com.cartrip.analyzer.cloud

import android.content.Context
import android.content.Intent
import com.cartrip.analyzer.analysis.TripAnalysis
import com.cartrip.analyzer.data.TripEntity
import com.cartrip.analyzer.export.ExportData
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CloudSync {

    const val SHEET_TITLE = "Car Trip Analyzer Log"
    private const val SUMMARY = "Summary"
    private const val SAMPLES = "Samples"
    private const val EVENTS = "Events"

    /** Append one trip's summary, samples and events to the user's Google Sheet. */
    suspend fun syncTrip(context: Context, trip: TripEntity, a: TripAnalysis): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val account = GoogleAuth.lastAccount(context)?.account
                    ?: return@withContext Result.failure(IllegalStateException("Not signed in"))
                val token = GoogleAuth.token(context, account)

                var id = CloudPrefs.spreadsheetId(context)
                if (id == null) {
                    id = SheetsClient.createSpreadsheet(
                        token, SHEET_TITLE, listOf(SUMMARY, SAMPLES, EVENTS)
                    )
                    SheetsClient.writeHeader(token, id, SUMMARY, ExportData.SUMMARY_HEADER)
                    SheetsClient.writeHeader(token, id, SAMPLES, ExportData.SAMPLE_HEADER)
                    SheetsClient.writeHeader(token, id, EVENTS, ExportData.EVENT_HEADER)
                    CloudPrefs.setSpreadsheetId(context, id)
                }

                SheetsClient.appendRows(token, id, SUMMARY, listOf(ExportData.summaryRow(trip, a)))
                SheetsClient.appendRows(token, id, SAMPLES, ExportData.sampleRows(trip.id, a, 5000))
                SheetsClient.appendRows(token, id, EVENTS, ExportData.eventRows(trip.id, a))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Forces an access-token fetch to trigger the one-time consent dialog.
     * Returns the consent Intent if the user must approve scopes, otherwise null.
     */
    suspend fun consentIntentIfNeeded(context: Context): Intent? = withContext(Dispatchers.IO) {
        val account = GoogleAuth.lastAccount(context)?.account ?: return@withContext null
        try {
            GoogleAuth.token(context, account)
            null
        } catch (e: UserRecoverableAuthException) {
            e.intent
        } catch (e: Exception) {
            null
        }
    }
}
