package com.example.data.remote

import android.util.Log
import androidx.core.text.HtmlCompat
import com.example.BuildConfig
import com.example.data.model.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class GeminiSummarizerService {

    private val tag = "GeminiSummarizer"
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    // Cache to prevent redundant Gemini API calls for already summarized articles
    private val summaryCache = ConcurrentHashMap<String, Pair<String, String>>() // id -> (summary, category)

    fun clearCache() {
        summaryCache.clear()
    }

    suspend fun summarizeArticle(article: Article): Article = withContext(Dispatchers.IO) {
        // Clean raw input text first
        val cleanTitle = cleanHtml(article.title)
        val cleanRawDesc = cleanHtml(article.rawDescription)

        // If already cached
        summaryCache[article.id]?.let { (cachedSummary, cachedCategory) ->
            return@withContext article.copy(
                title = cleanTitle.ifBlank { article.title },
                summary = cachedSummary,
                category = cachedCategory,
                isSummarizedByAi = true
            )
        }

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(tag, "Gemini API key is not configured or placeholder. Using robust RSS fallback.")
            val fallbackSummary = getFallbackSummary(cleanRawDesc.ifBlank { cleanTitle })
            val fallbackCategory = article.category.ifBlank { "LLMs" }
            summaryCache[article.id] = Pair(fallbackSummary, fallbackCategory)
            return@withContext article.copy(
                title = cleanTitle.ifBlank { article.title },
                summary = fallbackSummary,
                category = fallbackCategory,
                isSummarizedByAi = false
            )
        }

        try {
            val prompt = """
                You are a senior AI tech news editor for 'AI Shorts', an InShorts-style mobile reader.
                Write a complete, concise, self-contained summary of strictly 65 to 75 words. Ensure the last sentence ends with a complete thought and punctuation. Never leave an open or hanging sentence. Explain the breakthrough and its impact concisely.
                Also assign exactly ONE category tag from: [LLMs], [Robotics], [Hardware], [Open Source], [Vision], or [AI Policy].

                Strict Output Format:
                CATEGORY: [Chosen Category]
                SUMMARY: [Complete 65 to 75 word summary text ending with punctuation]

                Article Title: $cleanTitle
                Source: ${article.sourceName}
                Article Context: $cleanRawDesc
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
                val generationConfig = JSONObject().apply {
                    put("temperature", 0.3)
                    put("topP", 0.95)
                    put("maxOutputTokens", 300)
                }
                put("generationConfig", generationConfig)
            }

            val requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(requestUrl)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(tag, "Gemini API returned status code ${response.code}. Falling back safely.")
                    val fallbackSummary = getFallbackSummary(cleanRawDesc.ifBlank { cleanTitle })
                    return@withContext article.copy(
                        title = cleanTitle.ifBlank { article.title },
                        summary = fallbackSummary,
                        category = article.category.ifBlank { "LLMs" },
                        isSummarizedByAi = false
                    )
                }

                val responseStr = response.body?.string() ?: ""
                val responseJson = JSONObject(responseStr)
                val candidates = responseJson.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val generatedText = parts?.optJSONObject(0)?.optString("text")?.trim() ?: ""

                if (generatedText.isNotBlank()) {
                    val (extractedCategory, extractedSummary) = parseGeminiResponse(generatedText, cleanRawDesc.ifBlank { cleanTitle }, article.category)
                    summaryCache[article.id] = Pair(extractedSummary, extractedCategory)
                    return@withContext article.copy(
                        title = cleanTitle.ifBlank { article.title },
                        summary = extractedSummary,
                        category = extractedCategory,
                        isSummarizedByAi = true
                    )
                } else {
                    val fallbackSummary = getFallbackSummary(cleanRawDesc.ifBlank { cleanTitle })
                    return@withContext article.copy(
                        title = cleanTitle.ifBlank { article.title },
                        summary = fallbackSummary,
                        isSummarizedByAi = false
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error during Gemini summarization: ${e.message}. Using safe fallback.")
            val fallbackSummary = getFallbackSummary(cleanRawDesc.ifBlank { cleanTitle })
            return@withContext article.copy(
                title = cleanTitle.ifBlank { article.title },
                summary = fallbackSummary,
                isSummarizedByAi = false
            )
        }
    }

    private fun parseGeminiResponse(text: String, fallbackText: String, defaultCategory: String): Pair<String, String> {
        var category = defaultCategory.ifBlank { "LLMs" }
        val summaryBuilder = StringBuilder()

        val lines = text.lines()
        var capturingSummary = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("CATEGORY:", ignoreCase = true)) {
                val catRaw = trimmed.substringAfter(":").trim()
                    .removeSurrounding("[", "]")
                    .removeSurrounding("\"", "")
                    .removeSurrounding("**", "**")
                if (catRaw.isNotBlank()) {
                    category = normalizeCategory(catRaw)
                }
            } else if (trimmed.startsWith("SUMMARY:", ignoreCase = true)) {
                capturingSummary = true
                val initialText = trimmed.substringAfter(":").trim()
                if (initialText.isNotBlank()) {
                    summaryBuilder.append(initialText)
                }
            } else if (capturingSummary) {
                if (trimmed.isNotBlank()) {
                    if (summaryBuilder.isNotEmpty()) {
                        summaryBuilder.append(" ")
                    }
                    summaryBuilder.append(trimmed)
                }
            }
        }

        var finalSummary = cleanHtml(summaryBuilder.toString())

        if (finalSummary.isBlank()) {
            // If the model did not use the exact SUMMARY tag, extract clean content
            val cleanedRaw = text.replace("(?i)CATEGORY:.*".toRegex(), "")
                .replace("(?i)SUMMARY:", "")
                .trim()
            finalSummary = cleanHtml(cleanedRaw)
        }

        if (finalSummary.isBlank() || finalSummary.length < 20) {
            finalSummary = getFallbackSummary(fallbackText)
        }

        return Pair(category, finalSummary)
    }

    private fun normalizeCategory(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("robot") -> "Robotics"
            lower.contains("hardware") || lower.contains("chip") || lower.contains("gpu") || lower.contains("semiconductor") -> "Hardware"
            lower.contains("open source") || lower.contains("open-source") -> "Open Source"
            lower.contains("vision") || lower.contains("image") || lower.contains("video") || lower.contains("diffusion") -> "Vision"
            lower.contains("policy") || lower.contains("regulation") || lower.contains("law") || lower.contains("safety") || lower.contains("copyright") -> "AI Policy"
            else -> "LLMs"
        }
    }

    private fun cleanHtml(rawText: String): String {
        if (rawText.isBlank()) return ""
        return try {
            val spanned = HtmlCompat.fromHtml(rawText, HtmlCompat.FROM_HTML_MODE_LEGACY)
            spanned.toString()
                .replace('\uFFFC', ' ')
                .replace('\u00A0', ' ')
                .replace("\\s+".toRegex(), " ")
                .trim()
        } catch (e: Exception) {
            rawText.replace("<[^>]+>".toRegex(), " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .replace("\\s+".toRegex(), " ")
                .trim()
        }
    }

    private fun getFallbackSummary(sourceText: String): String {
        val clean = cleanHtml(sourceText)
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return "Stay tuned for further updates on this AI development."
        if (words.size <= 75) {
            val text = words.joinToString(" ")
            return if (text.endsWith(".") || text.endsWith("!") || text.endsWith("?")) text else "$text."
        }
        val slice = words.take(72).joinToString(" ")
        val lastPeriod = slice.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (lastPeriod > slice.length / 2) {
            slice.substring(0, lastPeriod + 1)
        } else {
            "$slice."
        }
    }
}
