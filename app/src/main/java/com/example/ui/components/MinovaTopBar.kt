package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ResponseStyle
import com.example.ui.theme.MinovaAzureLight
import com.example.ui.theme.MinovaCyanPrimary
import com.example.ui.theme.MinovaVioletAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinovaTopBar(
    title: String,
    subtitle: String? = null,
    showBackButton: Boolean = false,
    onMenuClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onNewChatClick: () -> Unit = {},
    onModelBadgeClick: () -> Unit = {},
    activeModelName: String = "MINOVA AI",
    activeResponseStyle: ResponseStyle = ResponseStyle.BALANCED,
    onResponseStyleClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left navigation + Brand mark + Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("top_bar_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onMenuClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("top_bar_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open navigation drawer",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Framed Geometric Brand Mark
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .border(
                                width = 1.dp,
                                color = MinovaCyanPrimary.copy(alpha = 0.32f),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.minova_icon),
                            contentDescription = "MINOVA AI Logo",
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("top_bar_logo")
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Title & Subtitle
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Right actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Model badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { onModelBadgeClick() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("top_bar_model_badge"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MinovaCyanPrimary)
                            )
                            Text(
                                text = activeModelName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Quick Response Style chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MinovaVioletAccent.copy(alpha = 0.14f))
                            .border(
                                width = 1.dp,
                                color = MinovaCyanPrimary.copy(alpha = 0.38f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { onResponseStyleClick() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("quick_response_style_chip"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Quick Response Style",
                                tint = MinovaCyanPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = activeResponseStyle.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // New Chat quick button
                    IconButton(
                        onClick = onNewChatClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MinovaCyanPrimary.copy(alpha = 0.14f))
                            .border(
                                width = 1.dp,
                                color = MinovaCyanPrimary.copy(alpha = 0.35f),
                                shape = CircleShape
                            )
                            .testTag("top_bar_new_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Conversation",
                            tint = MinovaCyanPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Subtle precision divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            )
        }
    }
}
