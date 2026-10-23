package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SuggestionCard
import com.example.ui.theme.MinovaAzureLight
import com.example.ui.theme.MinovaCyanPrimary
import com.example.ui.theme.MinovaVioletAccent

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmptyChatState(
    suggestions: List<SuggestionCard>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // MINOVA AI Central Geometric Brand Emblem
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MinovaCyanPrimary.copy(alpha = 0.55f),
                                MinovaVioletAccent.copy(alpha = 0.45f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.minova_icon),
                    contentDescription = "MINOVA AI Logo",
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("empty_state_logo")
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Main Greeting
            Text(
                text = "How can I help you today?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("empty_state_headline")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Official Brand Tagline
            Text(
                text = "Think. Create. Code. With MINOVA.",
                style = MaterialTheme.typography.bodyMedium,
                color = MinovaCyanPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.35.sp,
                modifier = Modifier.testTag("empty_state_tagline")
            )

            Spacer(modifier = Modifier.height(34.dp))

            // Suggestion Cards Grid / Flow
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 2
            ) {
                suggestions.forEach { suggestion ->
                    SuggestionCardItem(
                        suggestion = suggestion,
                        onClick = { onSuggestionClick(suggestion.prompt) },
                        modifier = Modifier
                            .widthIn(min = 260.dp, max = 320.dp)
                            .weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionCardItem(
    suggestion: SuggestionCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when (suggestion.id) {
        "explain" -> Icons.Default.Lightbulb
        "write_code" -> Icons.Default.Code
        "debug_code" -> Icons.Default.BugReport
        "study" -> Icons.Default.School
        else -> Icons.Default.AutoAwesome
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .testTag("suggestion_card_${suggestion.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MinovaVioletAccent.copy(alpha = 0.16f))
                        .border(
                            width = 1.dp,
                            color = MinovaCyanPrimary.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MinovaCyanPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = suggestion.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
            )
        }
    }
}
