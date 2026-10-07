package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_articles")
data class SavedArticle(
    @PrimaryKey val id: String,
    val title: String,
    val originalUrl: String,
    val sourceName: String,
    val imageUrl: String?,
    val publishedDateStr: String,
    val publishedTimestamp: Long,
    val summary: String,
    val category: String,
    val isSummarizedByAi: Boolean,
    val savedAtTimestamp: Long = System.currentTimeMillis()
)
