package com.example.data.repository

import com.example.data.local.SavedArticle
import com.example.data.local.SavedArticleDao
import com.example.data.model.Article
import com.example.data.remote.GeminiSummarizerService
import com.example.data.remote.RssFeedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NewsRepository(
    private val rssFeedService: RssFeedService,
    private val geminiSummarizerService: GeminiSummarizerService,
    private val savedArticleDao: SavedArticleDao
) {

    val savedArticles: Flow<List<SavedArticle>> = savedArticleDao.getAllSavedArticles()
    val savedArticleIds: Flow<List<String>> = savedArticleDao.getSavedArticleIds()

    fun clearCache() {
        geminiSummarizerService.clearCache()
    }

    suspend fun fetchFreshArticles(): List<Article> = withContext(Dispatchers.IO) {
        val rawArticles = rssFeedService.fetchAllFeeds()
        // Return raw articles immediately so UI displays fast, summarization can happen smoothly
        rawArticles
    }

    suspend fun processGeminiSummary(article: Article): Article = withContext(Dispatchers.IO) {
        geminiSummarizerService.summarizeArticle(article)
    }

    suspend fun bookmarkArticle(article: Article) = withContext(Dispatchers.IO) {
        val savedEntity = SavedArticle(
            id = article.id,
            title = article.title,
            originalUrl = article.originalUrl,
            sourceName = article.sourceName,
            imageUrl = article.imageUrl,
            publishedDateStr = article.publishedDateStr,
            publishedTimestamp = article.publishedTimestamp,
            summary = article.summary,
            category = article.category,
            isSummarizedByAi = article.isSummarizedByAi,
            savedAtTimestamp = System.currentTimeMillis()
        )
        savedArticleDao.insertArticle(savedEntity)
    }

    suspend fun removeBookmark(articleId: String) = withContext(Dispatchers.IO) {
        savedArticleDao.deleteArticleById(articleId)
    }

    suspend fun toggleBookmark(article: Article, isCurrentlySaved: Boolean) {
        if (isCurrentlySaved) {
            removeBookmark(article.id)
        } else {
            bookmarkArticle(article)
        }
    }
}
