package com.assistant.board

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/** Fully on-device pipeline: extract text locally, summarize via a cloud LLM. */
object Summarizer {

    const val PREFS = "aiboard"
    val PROVIDERS = listOf("GEMINI", "GROK", "HF")
    private val DEFAULT_MODEL = mapOf(
        "GEMINI" to "gemini-1.5-flash",
        "GROK" to "grok-2-latest",
        "HF" to "meta-llama/Llama-3.1-8B-Instruct"
    )

    private var pdfInit = false

    fun providerLabel(p: String) = when (p) {
        "GEMINI" -> "Google Gemini"
        "GROK" -> "xAI Grok"
        "HF" -> "Hugging Face"
        else -> p
    }

    fun defaultModel(p: String) = DEFAULT_MODEL[p] ?: ""

    fun hasToken(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val p = prefs.getString("provider", "GEMINI") ?: "GEMINI"
        return !prefs.getString("token_$p", "").isNullOrBlank()
    }

    fun currentProvider(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("provider", "GEMINI") ?: "GEMINI"

    fun extractText(context: Context, file: File): String =
        when (file.extension.lowercase()) {
            "txt", "md" -> runCatching { file.readText() }.getOrDefault("")
            "pdf" -> extractPdf(context, file)
            "docx", "pptx", "xlsx" -> extractOoxml(file)
            else -> ""
        }

    private fun extractPdf(context: Context, file: File): String {
        if (!pdfInit) {
            PDFBoxResourceLoader.init(context.applicationContext); pdfInit = true
        }
        return runCatching {
            PDDocument.load(file).use { doc ->
                val stripper = PDFTextStripper()
                stripper.startPage = 1
                stripper.endPage = minOf(doc.numberOfPages, 5)
                stripper.getText(doc)
            }
        }.getOrDefault("")
    }

    private fun extractOoxml(file: File): String = runCatching {
        val sb = StringBuilder()
        ZipFile(file).use { z ->
            val names = z.entries().toList()
            for (entry in names) {
                val n = entry.name
                if (n.endsWith(".xml") && (n.contains("document") || n.contains("slide") ||
                            n.contains("sharedStrings") || n.contains("/worksheets/"))
                ) {
                    val raw = z.getInputStream(entry).bufferedReader().readText()
                    var intag = false
                    for (ch in raw) {
                        when (ch) {
                            '<' -> intag = true
                            '>' -> { intag = false; sb.append(' ') }
                            else -> if (!intag) sb.append(ch)
                        }
                    }
                }
            }
        }
        sb.toString()
    }.getOrDefault("")

    /** Extract + summarize. Throws IllegalStateException if not configured. */
    fun summarizeFile(context: Context, file: File): String {
        val text = extractText(context, file)
        return summarizeText(context, file.name, text)
    }

    fun summarizeText(context: Context, filename: String, text: String): String {
        val trimmed = text.replace(Regex("\\s+"), " ").trim().take(4000)
        if (trimmed.isEmpty()) return "(no readable text could be extracted)"

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val provider = prefs.getString("provider", "GEMINI") ?: "GEMINI"
        val token = prefs.getString("token_$provider", "") ?: ""
        if (token.isBlank()) {
            throw IllegalStateException("No API token set for ${providerLabel(provider)}. Open Settings.")
        }
        val model = prefs.getString("model_$provider", defaultModel(provider))!!
            .ifBlank { defaultModel(provider) }
        val prompt = "Summarize the following document in ONE short line (max 15 words). " +
                "Reply with only the summary, no preamble.\nFilename: $filename\n\nContent:\n$trimmed"

        return when (provider) {
            "GEMINI" -> gemini(token, model, prompt)
            "GROK" -> openai("https://api.x.ai/v1/chat/completions", token, model, prompt)
            "HF" -> openai("https://router.huggingface.co/v1/chat/completions", token, model, prompt)
            else -> throw IllegalStateException("Unknown provider")
        }
    }

    private fun clean(s: String) = s.replace(Regex("\\s+"), " ").trim().trim('"').ifEmpty { "(empty)" }

    private fun httpPost(urlStr: String, body: String, headers: Map<String, String>): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw RuntimeException("HTTP $code: ${resp.take(180)}")
        return resp
    }

    private fun gemini(token: String, model: String, prompt: String): String {
        val body = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", prompt))))
        ).toString()
        val resp = httpPost(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$token",
            body, mapOf("Content-Type" to "application/json")
        )
        val out = JSONObject(resp).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        return clean(out)
    }

    private fun openai(url: String, token: String, model: String, prompt: String): String {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.2)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .toString()
        val resp = httpPost(url, body,
            mapOf("Content-Type" to "application/json", "Authorization" to "Bearer $token"))
        val out = JSONObject(resp).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        return clean(out)
    }
}
