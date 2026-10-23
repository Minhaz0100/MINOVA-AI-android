package com.example.ui.components

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
import com.example.navigation.MinovaScreen
import com.example.ui.theme.MinovaAzureLight
import com.example.ui.theme.MinovaCyanPrimary

@Composable
fun MinovaDrawerContent(
    conversations: List<Conversation>,
    activeConversationId: String?,
    userProfile: UserProfile,
    onNavigate: (MinovaScreen) -> Unit,
    onSelectConversation: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onTogglePin: (String) -> Unit,
    onToggleArchive: (String) -> Unit = {},
    onDeleteConversation: (String) -> Unit = {},
    drawerWidth: Dp? = null,
    modifier: Modifier = Modifier
) {
    var conversationToDelete by remember { mutableStateOf<Conversation?>(null) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val resolvedDrawerWidth = (drawerWidth ?: (screenWidthDp * 0.70f)).coerceIn(220.dp, 280.dp)

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(resolvedDrawerWidth)
            .widthIn(min = 220.dp, max = 280.dp)
            .onSizeChanged { size ->
                val measuredWidthDp = with(density) { size.width.toDp() }
                val measuredHeightDp = with(density) { size.height.toDp() }
                try {
                    Log.d(
                        "MinovaDrawerWidth",
                        "Measured drawer Surface width=${measuredWidthDp.value}dp (${size.width}px), " +
                            "height=${measuredHeightDp.value}dp (${size.height}px), targetWidth=${resolvedDrawerWidth.value}dp, " +
                            "screenWidth=${screenWidthDp.value}dp"
                    )
                } catch (_: Throwable) {
                    // Ignore Log stub exceptions in unit tests
                }
            }
            .testTag("minova_drawer_surface"),
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Start)
                )
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
                // Header: Brand & New Chat
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .border(
                                    width = 1.dp,
                                    color = MinovaCyanPrimary.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(11.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.minova_icon),
                                contentDescription = "MINOVA AI Logo",
                                modifier = Modifier
                                    .size(26.dp)
                                    .testTag("drawer_logo")
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "MINOVA AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Think. Create. Code.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MinovaCyanPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Prominent "New Chat" button
                    Button(
                        onClick = onNewChatClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("drawer_new_chat_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MinovaCyanPrimary,
                            contentColor = Color(0xFF04131E)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Chat",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Scrollable middle section: Quick Nav Links + Recent Chats by Category
                val nonArchived = conversations.filterNot { it.isArchived }
                val categories = listOf("Today", "Yesterday", "Previous 7 days")

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Quick Nav Links: Learn Mode, Prompt Library, Search, Pinned, Archived
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                    ) {
                        DrawerNavItem(
                            icon = Icons.Default.School,
                            label = "Learn Mode",
                            testTag = "drawer_nav_learn_mode",
                            badge = "AI",
                            onClick = { onNavigate(MinovaScreen.LearnHome) }
                        )
                        DrawerNavItem(
                            icon = Icons.Default.Code,
                            label = "Code Workspace",
                            testTag = "drawer_nav_code_workspace",
                            badge = "IDE",
                            onClick = { onNavigate(MinovaScreen.CodeWorkspace) }
                        )
                        DrawerNavItem(
                            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                            label = "AI File Studio",
                            testTag = "drawer_nav_file_studio",
                            badge = "DOCS",
                            onClick = { onNavigate(MinovaScreen.FileStudio) }
                        )
                        DrawerNavItem(
                            icon = Icons.Default.AutoAwesome,
                            label = "Prompt Library",
                            testTag = "drawer_nav_prompt_library",
                            onClick = { onNavigate(MinovaScreen.PromptLibrary) }
                        )
                        DrawerNavItem(
                            icon = Icons.Default.Search,
                            label = "Search Conversations",
                            testTag = "drawer_nav_search",
                            onClick = { onNavigate(MinovaScreen.Search) }
                        )
                        DrawerNavItem(
                            icon = Icons.Default.PushPin,
                            label = "Pinned Chats",
                            testTag = "drawer_nav_pinned",
                            badge = conversations.count { it.isPinned }.takeIf { it > 0 }?.toString(),
                            onClick = { onNavigate(MinovaScreen.Pinned) }
                        )
                        DrawerNavItem(
                            icon = Icons.Default.Archive,
                            label = "Archived Chats",
                            testTag = "drawer_nav_archived",
                            badge = conversations.count { it.isArchived }.takeIf { it > 0 }?.toString(),
                            onClick = { onNavigate(MinovaScreen.Archived) }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .padding(horizontal = 14.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        if (nonArchived.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No chats yet. Start a new chat!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        } else {
                            categories.forEach { category ->
                                val chatsInCategory = nonArchived.filter { it.category == category }
                                if (chatsInCategory.isNotEmpty()) {
                                    Text(
                                        text = category,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )

                                    chatsInCategory.forEach { conv ->
                                        val isSelected = conv.id == activeConversationId
                                        ConversationDrawerItem(
                                            conversation = conv,
                                            isSelected = isSelected,
                                            onSelect = { onSelectConversation(conv.id) },
                                            onTogglePin = { onTogglePin(conv.id) },
                                            onToggleArchive = { onToggleArchive(conv.id) },
                                            onRequestDelete = { conversationToDelete = conv }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Divider before footer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                )

                // Drawer Footer: Profile & Settings (kept completely above the system navigation bar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Profile link
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onNavigate(MinovaScreen.Profile) }
                            .padding(6.dp)
                            .testTag("drawer_profile_link"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MinovaAzureLight.copy(alpha = 0.2f))
                                .border(1.dp, MinovaAzureLight.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MinovaAzureLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "MINOVA PRO",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MinovaCyanPrimary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Settings Icon button
                    IconButton(
                        onClick = { onNavigate(MinovaScreen.Settings) },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("drawer_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

    // Delete Confirmation Dialog
    conversationToDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = {
                Text(
                    text = "Delete Chat?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${conv.title}\"? All messages in this conversation will be permanently removed.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = conv.id
                        conversationToDelete = null
                        onDeleteConversation(id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { conversationToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DrawerNavItem(
    icon: ImageVector,
    label: String,
    testTag: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MinovaAzureLight,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MinovaCyanPrimary.copy(alpha = 0.14f))
                    .border(
                        width = 0.8.dp,
                        color = MinovaCyanPrimary.copy(alpha = 0.35f),
                        shape = CircleShape
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MinovaCyanPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ConversationDrawerItem(
    conversation: Conversation,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onSelect() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("drawer_conv_${conversation.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = if (isSelected) MinovaAzureLight else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (conversation.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (conversation.isPinned) "Unpin" else "Pin",
                    tint = if (conversation.isPinned) MinovaCyanPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.size(14.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("conv_menu_${conversation.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (conversation.isPinned) "Unpin" else "Pin") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (conversation.isPinned) Icons.Outlined.PushPin else Icons.Default.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (conversation.isArchived) "Unarchive" else "Archive") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (conversation.isArchived) Icons.Outlined.Archive else Icons.Default.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onToggleArchive()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRequestDelete()
                        }
                    )
                }
            }
        }
    }
}
