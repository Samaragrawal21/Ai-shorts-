package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.Article
import com.example.ui.components.NewsCard
import com.example.ui.theme.AIShortsTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    val sampleArticle = Article(
      id = "test_1",
      title = "AI Model Breakthrough in Real-Time Reasoning",
      originalUrl = "https://example.com/ai-news",
      sourceName = "TechCrunch",
      imageUrl = null,
      publishedDateStr = "Recent",
      publishedTimestamp = System.currentTimeMillis() - 7200000L,
      rawDescription = "Researchers demonstrate ultra-fast sub-100ms multimodal inference.",
      summary = "Researchers unveiled next-generation multimodal neural architectures capable of native code execution and human-level tool calling in sub-100ms latencies. The new benchmarks demonstrate significant gains in mathematical reasoning, cross-lingual context handling, and autonomous agent orchestration across enterprise workflows.",
      category = "LLMs",
      isSummarizedByAi = true
    )

    composeTestRule.setContent {
      AIShortsTheme {
        NewsCard(
          article = sampleArticle,
          onBookmarkToggle = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}

