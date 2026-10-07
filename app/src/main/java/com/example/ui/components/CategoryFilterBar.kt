package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AICategory
import com.example.ui.theme.AccentTag
import com.example.ui.theme.AccentTagBorder
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.HighlightBlue
import com.example.ui.theme.PrimaryText
import com.example.ui.theme.SecondaryBody

@Composable
fun CategoryFilterBar(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val categories = AICategory.values()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.forEach { category ->
            val isSelected = category.displayName.equals(selectedCategory, ignoreCase = true)
            
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) HighlightBlue else AccentTag,
                animationSpec = tween(durationMillis = 200),
                label = "chip_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) DarkBackground else SecondaryBody,
                animationSpec = tween(durationMillis = 200),
                label = "chip_text"
            )
            val borderColor = if (isSelected) HighlightBlue else AccentTagBorder

            Box(
                modifier = Modifier
                    .testTag("category_chip_${category.name.lowercase()}")
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                    .clickable { onCategorySelected(category.displayName) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.displayName,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
