package com.example.data.model

data class Article(
    val id: String,
    val title: String,
    val originalUrl: String,
    val sourceName: String,
    val imageUrl: String? = null,
    val publishedDateStr: String = "",
    val publishedTimestamp: Long = System.currentTimeMillis(),
    val rawDescription: String = "",
    val summary: String = "",
    val category: String = "LLMs",
    val isSummarizedByAi: Boolean = false,
    val isSaved: Boolean = false
)

enum class AICategory(val displayName: String) {
    ALL("All"),
    LLMS("LLMs"),
    ROBOTICS("Robotics"),
    HARDWARE("Hardware"),
    OPEN_SOURCE("Open Source"),
    VISION("Vision"),
    POLICY("AI Policy");

    companion object {
        fun fromTag(tag: String): AICategory {
            val cleaned = tag.trim().removeSurrounding("[", "]").removeSurrounding("\"", "")
            return values().firstOrNull { 
                it.displayName.equals(cleaned, ignoreCase = true) ||
                it.name.equals(cleaned.replace(" ", "_"), ignoreCase = true)
            } ?: LLMS
        }
    }
}
