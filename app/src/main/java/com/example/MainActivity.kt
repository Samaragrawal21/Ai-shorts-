package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AICategory
import com.example.ui.components.CategoryFilterBar
import com.example.ui.components.EmptyOrErrorFeedView
import com.example.ui.components.LoadingFeedView
import com.example.ui.components.NewsCard
import com.example.ui.components.SavedArticlesView
import com.example.ui.theme.AIShortsTheme
import com.example.ui.theme.AccentTag
import com.example.ui.theme.AccentTagBorder
import com.example.ui.theme.BookmarkGold
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.HighlightBlue
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryBody
import com.example.ui.viewmodel.NewsViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIShortsTheme {
                AIShortsApp()
            }
        }
    }
}

@Composable
fun AIShortsApp(
    viewModel: NewsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedArticles by viewModel.savedArticles.collectAsStateWithLifecycle()
    var isSavedViewOpen by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground,
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isSavedViewOpen) {
                BackHandler { isSavedViewOpen = false }

                SavedArticlesTopAppBar(
                    savedCount = savedArticles.size,
                    onBack = { isSavedViewOpen = false }
                )

                SavedArticlesView(
                    savedArticles = savedArticles,
                    onDeleteSaved = { viewModel.deleteSavedArticle(it) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                val articles = uiState.filteredArticles
                val pagerState = rememberPagerState(
                    initialPage = 0,
                    pageCount = { articles.size }
                )

                // Top App Bar maximizing viewport reading area with Saved Offline pill and Reload icon button
                AIShortsTopAppBar(
                    savedCount = savedArticles.size,
                    isRefreshing = uiState.isRefreshing,
                    onOpenSaved = { isSavedViewOpen = true },
                    onRefresh = {
                        coroutineScope.launch {
                            if (articles.isNotEmpty()) {
                                pagerState.scrollToPage(0)
                            }
                            viewModel.refreshFeed {
                                coroutineScope.launch {
                                    if (pagerState.pageCount > 0) {
                                        pagerState.scrollToPage(0)
                                    }
                                }
                            }
                        }
                    }
                )

                // Category Filter Bar
                CategoryFilterBar(
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = { viewModel.selectCategory(it) }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        uiState.isLoading && uiState.allArticles.isEmpty() -> {
                            LoadingFeedView()
                        }
                        uiState.filteredArticles.isEmpty() && uiState.allArticles.isNotEmpty() -> {
                            EmptyOrErrorFeedView(
                                message = "No recent articles tagged under '${uiState.selectedCategory}'.",
                                onRetry = { viewModel.refreshFeed() },
                                onResetFilter = { viewModel.selectCategory(AICategory.ALL.displayName) }
                            )
                        }
                        uiState.filteredArticles.isEmpty() -> {
                            EmptyOrErrorFeedView(
                                message = uiState.errorMessage ?: "No AI news feeds available. Check connection and retry.",
                                onRetry = { viewModel.refreshFeed() }
                            )
                        }
                        else -> {
                            VerticalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("news_vertical_pager"),
                                flingBehavior = PagerDefaults.flingBehavior(
                                    state = pagerState,
                                    snapPositionalThreshold = 0.15f
                                ),
                                key = { index -> articles.getOrNull(index)?.id ?: index }
                            ) { page ->
                                val article = articles.getOrNull(page)
                                if (article != null) {
                                    NewsCard(
                                        article = article,
                                        onBookmarkToggle = { viewModel.toggleBookmark(it) }
                                    )
                                }
                            }
                        }
                    }

                    // Loading spinner overlay during full refresh
                    if (uiState.isRefreshing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkBackground.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = HighlightBlue,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(46.dp)
                                )
                                Text(
                                    text = "Refreshing AI feeds & generating summaries...",
                                    color = PrimaryText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AIShortsTopAppBar(
    savedCount: Int,
    isRefreshing: Boolean,
    onOpenSaved: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin_anim")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate_refresh"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(DarkBackground)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Side: Clickable pill button with Bookmark icon + "Saved Offline (Tap to view)"
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(AccentTag)
                .border(1.dp, AccentTagBorder, RoundedCornerShape(20.dp))
                .clickable { onOpenSaved() }
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .testTag("saved_offline_button"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = "Saved offline summaries",
                tint = BookmarkGold,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (savedCount > 0) "Saved Offline ($savedCount)" else "Saved Offline (Tap to view)",
                color = PrimaryText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Right Side: Reload icon button (↻)
        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .testTag("refresh_button")
                .size(42.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh news feeds",
                tint = if (isRefreshing) HighlightBlue else SecondaryBody,
                modifier = Modifier
                    .size(22.dp)
                    .then(if (isRefreshing) Modifier.rotate(rotation) else Modifier)
            )
        }
    }
}

@Composable
fun SavedArticlesTopAppBar(
    savedCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(DarkBackground)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("back_to_feed_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back to Feed",
                tint = PrimaryText,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = "Saved Summaries ($savedCount)",
            color = PrimaryText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}
