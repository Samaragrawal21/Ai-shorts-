package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SavedArticle
import com.example.data.model.AICategory
import com.example.data.model.Article
import com.example.data.remote.GeminiSummarizerService
import com.example.data.remote.RssFeedService
import com.example.data.repository.NewsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val allArticles: List<Article> = emptyList(),
    val filteredArticles: List<Article> = emptyList(),
    val selectedCategory: String = AICategory.ALL.displayName,
    val savedArticleIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val currentTab: Int = 0 // 0: Feed, 1: Saved
)

class NewsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NewsRepository
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var summaryJob: Job? = null

    val savedArticles: StateFlow<List<SavedArticle>>

    init {
        val database = AppDatabase.getInstance(application)
        repository = NewsRepository(
            rssFeedService = RssFeedService(),
            geminiSummarizerService = GeminiSummarizerService(),
            savedArticleDao = database.savedArticleDao()
        )

        savedArticles = repository.savedArticles.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Observe saved article IDs to update bookmark status reactively
        viewModelScope.launch {
            repository.savedArticleIds.collect { ids ->
                val idSet = ids.toSet()
                _uiState.update { state ->
                    val updatedAll = state.allArticles.map { it.copy(isSaved = idSet.contains(it.id)) }
                    val updatedFiltered = state.filteredArticles.map { it.copy(isSaved = idSet.contains(it.id)) }
                    state.copy(
                        savedArticleIds = idSet,
                        allArticles = updatedAll,
                        filteredArticles = updatedFiltered
                    )
                }
            }
        }

        loadArticles(isRefresh = false)
    }

    fun refreshFeed(onCompleted: (() -> Unit)? = null) {
        summaryJob?.cancel()
        repository.clearCache()
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            try {
                val articles = repository.fetchFreshArticles()
                val savedIds = _uiState.value.savedArticleIds
                val mappedArticles = articles.map { it.copy(isSaved = savedIds.contains(it.id)) }

                val selectedCategory = _uiState.value.selectedCategory
                val filtered = filterArticlesByCategory(mappedArticles, selectedCategory)

                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isLoading = false,
                        allArticles = mappedArticles,
                        filteredArticles = filtered,
                        errorMessage = if (mappedArticles.isEmpty()) "No recent articles found within 48h." else null
                    )
                }

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onCompleted?.invoke()
                }

                startGeminiSummarizationPipeline(mappedArticles)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isLoading = false,
                        errorMessage = "Unable to refresh news: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun loadArticles(isRefresh: Boolean = false) {
        if (isRefresh) {
            refreshFeed()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { 
                it.copy(isLoading = true, errorMessage = null)
            }

            try {
                val articles = repository.fetchFreshArticles()
                val savedIds = _uiState.value.savedArticleIds
                val mappedArticles = articles.map { it.copy(isSaved = savedIds.contains(it.id)) }

                val selectedCategory = _uiState.value.selectedCategory
                val filtered = filterArticlesByCategory(mappedArticles, selectedCategory)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        allArticles = mappedArticles,
                        filteredArticles = filtered,
                        errorMessage = if (mappedArticles.isEmpty()) "No recent articles found within 48h." else null
                    )
                }

                // Launch background AI summarization for top articles
                startGeminiSummarizationPipeline(mappedArticles)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = "Unable to fetch news: ${e.localizedMessage ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    private fun startGeminiSummarizationPipeline(articles: List<Article>) {
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch(Dispatchers.IO) {
            // Process top 10 articles first for lightning-fast user experience
            articles.take(15).forEach { article ->
                if (!article.isSummarizedByAi) {
                    try {
                        val summarized = repository.processGeminiSummary(article)
                        updateSingleArticle(summarized)
                    } catch (e: Exception) {
                        // Safe fallback is already returned inside repository
                    }
                }
            }
        }
    }

    private fun updateSingleArticle(updatedArticle: Article) {
        _uiState.update { state ->
            val updatedAll = state.allArticles.map { 
                if (it.id == updatedArticle.id) updatedArticle.copy(isSaved = state.savedArticleIds.contains(it.id))
                else it 
            }
            val filtered = filterArticlesByCategory(updatedAll, state.selectedCategory)
            state.copy(
                allArticles = updatedAll,
                filteredArticles = filtered
            )
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { state ->
            val filtered = filterArticlesByCategory(state.allArticles, category)
            state.copy(
                selectedCategory = category,
                filteredArticles = filtered
            )
        }
    }

    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(currentTab = tabIndex) }
    }

    fun toggleBookmark(article: Article) {
        viewModelScope.launch(Dispatchers.IO) {
            val isCurrentlySaved = _uiState.value.savedArticleIds.contains(article.id)
            repository.toggleBookmark(article, isCurrentlySaved)
        }
    }

    fun deleteSavedArticle(articleId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeBookmark(articleId)
        }
    }

    private fun filterArticlesByCategory(articles: List<Article>, category: String): List<Article> {
        if (category.equals(AICategory.ALL.displayName, ignoreCase = true)) {
            return articles
        }
        return articles.filter { article ->
            article.category.equals(category, ignoreCase = true) ||
            AICategory.fromTag(article.category).displayName.equals(category, ignoreCase = true)
        }
    }
}
