package com.example.ui.components

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Article
import com.example.ui.theme.AccentTagBorder
import com.example.ui.theme.BookmarkGold
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.HighlightBlue
import com.example.ui.theme.SecondaryBody
import com.example.ui.util.CustomTabsUtil
import com.example.ui.util.TimeUtils
import kotlin.math.roundToInt

@Composable
fun NewsCard(
    article: Article,
    onBookmarkToggle: (Article) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var totalDragAmount by remember { mutableFloatStateOf(0f) }
    var imageLoadFailed by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "hint_anim")
    val hintOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_offset"
    )

    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("news_card_${article.id}")
            .pointerInput(article.id) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDragAmount = 0f },
                    onDragEnd = {
                        // Swipe Left gesture detected (drag distance threshold: -100px)
                        if (totalDragAmount < -100f) {
                            CustomTabsUtil.openArticle(context, article.originalUrl)
                        }
                        totalDragAmount = 0f
                    },
                    onDragCancel = { totalDragAmount = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        totalDragAmount += dragAmount
                    }
                )
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.verticalGradient(
                colors = listOf(AccentTagBorder, Color(0xFF161C2C))
            )
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Image Component (Constrained dynamically: heightIn 140dp-165dp, ContentScale.Crop, rounded 14dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 165.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(DarkSurfaceVariant)
            ) {
                if (!article.imageUrl.isNullOrBlank() && !imageLoadFailed) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(article.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Article image thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 165.dp),
                        onError = { imageLoadFailed = true }
                    )
                } else {
                    AbstractGeometricFallback(category = article.category)
                }

                // Overlay "Source" Badge: Position a semi-transparent dark pill (e.g. [TechCrunch]) inside bottom-left corner of image
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xDD0A0D14))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "[${article.sourceName}]",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // 2. Middle Content: Metadata Row + Title + Divider + Body Summary
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // Metadata Row (Directly below Image)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Time with clock icon in muted secondary text (#8E9AA8, 12sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF8E9AA8),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = TimeUtils.getRelativeTimeSpan(article.publishedTimestamp),
                            color = Color(0xFF8E9AA8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )

                        if (article.isSummarizedByAi) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x1F8AB4F8))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = HighlightBlue,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "Gemini",
                                    color = HighlightBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Right: Category pill (e.g. "[LLMs]") with accent border (#202738 surface, #00E5FF text, 11sp)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF202738))
                            .border(1.dp, Color(0xFF2D384E), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "[${article.category}]",
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Bold Headline: 18sp, font-weight Bold, color White, maxLines = 2
                Text(
                    text = article.title,
                    color = Color.White,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            CustomTabsUtil.openArticle(context, article.originalUrl)
                        }
                )

                // Subtle horizontal divider right below the title
                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color(0xFF1E2638),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Body Summary: Modifier.weight(1f).verticalScroll(rememberScrollState()), 14.5sp, 21sp line-height, #E0E6ED
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = article.summary.ifBlank { article.rawDescription },
                        color = Color(0xFFE0E6ED),
                        fontSize = 14.5.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.2.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 3. Bottom Action Bar: Row pinned to bottom of card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color(0xFF0E121C))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Bookmark Icon (Left): Saves/unsaves the article locally into a Room Database
                val isBookmarked = article.isSaved
                val bookmarkColor by animateColorAsState(
                    targetValue = if (isBookmarked) BookmarkGold else SecondaryBody,
                    label = "bookmark_color"
                )

                IconButton(
                    onClick = { onBookmarkToggle(article) },
                    modifier = Modifier
                        .testTag("bookmark_button_${article.id}")
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark" else "Bookmark article",
                        tint = bookmarkColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Center Hint: Subtle text "< SWIPE FOR FULL ARTICLE"
                Row(
                    modifier = Modifier
                        .testTag("swipe_full_article_hint")
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            CustomTabsUtil.openArticle(context, article.originalUrl)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = HighlightBlue,
                        modifier = Modifier
                            .size(13.dp)
                            .offset { IntOffset(hintOffset.roundToInt(), 0) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SWIPE FOR FULL ARTICLE",
                        color = HighlightBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }

                // Share Button (Right)
                IconButton(
                    onClick = {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "${article.title}\n\n${article.summary}\n\nRead full story on ${article.sourceName}: ${article.originalUrl}"
                            )
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share AI Short"))
                    },
                    modifier = Modifier
                        .testTag("share_button_${article.id}")
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share article",
                        tint = SecondaryBody,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AbstractGeometricFallback(category: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF141C2E),
                        Color(0xFF1A2640),
                        Color(0xFF0F1726)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            drawLine(
                color = Color(0x1A8AB4F8),
                start = Offset(0f, 0f),
                end = Offset(canvasWidth, canvasHeight),
                strokeWidth = 2f
            )
            drawLine(
                color = Color(0x1AC58AF9),
                start = Offset(canvasWidth, 0f),
                end = Offset(0f, canvasHeight),
                strokeWidth = 2f
            )
            drawCircle(
                color = Color(0x128AB4F8),
                radius = canvasHeight * 0.45f,
                center = Offset(canvasWidth * 0.5f, canvasHeight * 0.5f)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = HighlightBlue.copy(alpha = 0.8f),
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "AI SHORTS • $category",
                color = SecondaryBody.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
            Text(
                text = "No Image Available",
                color = SecondaryBody.copy(alpha = 0.4f),
                fontSize = 10.sp
            )
        }
    }
}
