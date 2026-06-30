package com.cartrip.analyzer.cloud

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Minimal Google Sheets v4 REST client (no heavy Google API client lib). */
object SheetsClient {

    private const val BASE = "https://sheets.googleapis.com/v4/spreadsheets"
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun createSpreadsheet(token: String, title: String, sheetTitles: List<String>): String {
        val sheets = JSONArray()
        sheetTitles.forEach {
            sheets.put(JSONObject().put("properties", JSONObject().put("title", it)))
        }
        val body = JSONObject()
            .put("properties", JSONObject().put("title", title))
            .put("sheets", sheets)
        val resp = send("POST", BASE, token, body)
        return JSONObject(resp).getString("spreadsheetId")
    }

    fun writeHeader(token: String, spreadsheetId: String, sheet: String, header: List<String>) {
        val range = "${enc(sheet)}!A1"
        val url = "$BASE/$spreadsheetId/values/$range?valueInputOption=RAW"
        val body = JSONObject().put("values", JSONArray().put(JSONArray(header)))
        send("PUT", url, token, body)
    }

    fun appendRows(token: String, spreadsheetId: String, sheet: String, rows: List<List<String>>) {
        if (rows.isEmpty()) return
        val range = "${enc(sheet)}!A1"
        val url = "$BASE/$spreadsheetId/values/$range:append" +
            "?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS"
        val values = JSONArray()
        rows.forEach { values.put(JSONArray(it)) }
        val body = JSONObject().put("values", values)
        send("POST", url, token, body)
    }

    private fun send(method: String, url: String, token: String, body: JSONObject): String {
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .method(method, body.toString().toRequestBody(JSON))
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("Sheets API ${resp.code}: $text")
            return text
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
