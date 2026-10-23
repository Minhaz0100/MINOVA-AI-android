package com.example.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.ResponseFormat
import com.example.data.model.ResponseLanguage
import com.example.data.model.ResponseStyle
import com.example.data.model.SettingsState
import com.example.navigation.MinovaScreen
import com.example.ui.theme.MinovaAzureLight
import com.example.ui.theme.MinovaCyanPrimary
import com.example.ui.theme.MinovaError
import com.example.ui.theme.MinovaIndigoSecondary
import com.example.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    settings: SettingsState,
    onSettingsChange: (SettingsState) -> Unit,
    onResetSettings: () -> Unit = {},
    onBack: () -> Unit,
    onNavigate: (MinovaScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appVersion = remember(context) {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "1.0.0"
        } catch (_: Throwable) {
            "1.0.0"
        }
    }

    var showResponseStyleDialog by remember { mutableStateOf(false) }
    var showResponseLanguageDialog by remember { mutableStateOf(false) }
    var showResponseFormatDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    val availableLanguages = listOf(
        "System Default",
        "বাংলা (Bangladesh)",
        "English (US)",
        "English (UK)"
    )

    val speedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f)

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Settings & Personalization",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Section 1: GENERAL (Theme)
                SettingsSectionHeader(title = "GENERAL", icon = Icons.Default.Palette)
                CardContainer {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemeOptionButton(
                            label = "System Default",
                            isSelected = settings.themeMode == ThemeMode.SYSTEM,
                            onClick = { onSettingsChange(settings.copy(themeMode = ThemeMode.SYSTEM)) },
                            modifier = Modifier.weight(1f),
                            testTag = "theme_option_system"
                        )
                        ThemeOptionButton(
                            label = "Dark",
                            isSelected = settings.themeMode == ThemeMode.DARK,
                            onClick = { onSettingsChange(settings.copy(themeMode = ThemeMode.DARK)) },
                            modifier = Modifier.weight(1f),
                            testTag = "theme_option_dark"
                        )
                        ThemeOptionButton(
                            label = "Light",
                            isSelected = settings.themeMode == ThemeMode.LIGHT,
                            onClick = { onSettingsChange(settings.copy(themeMode = ThemeMode.LIGHT)) },
                            modifier = Modifier.weight(1f),
                            testTag = "theme_option_light"
                        )
                    }

                    SettingsDivider()

                    SettingsNavigationRow(
                        title = "Account & Cloud Sync",
                        subtitle = "Manage sign-in, local-only mode, consent, and multi-device sync",
                        testTag = "settings_account_cloud_sync_row",
                        onClick = { onNavigate(MinovaScreen.Profile) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 2: AI RESPONSE (Response Style, Response Language, Response Format)
                SettingsSectionHeader(title = "AI RESPONSE", icon = Icons.Default.AutoAwesome)
                CardContainer {
                    SettingsNavigationRow(
                        title = "Response Style",
                        subtitle = "${settings.responseStyle.displayName} — ${settings.responseStyle.description}",
                        testTag = "settings_response_style_row",
                        onClick = { showResponseStyleDialog = true }
                    )

                    SettingsDivider()

                    SettingsNavigationRow(
                        title = "Response Language",
                        subtitle = "${settings.responseLanguage.displayName} — ${settings.responseLanguage.description}",
                        testTag = "settings_response_language_row",
                        onClick = { showResponseLanguageDialog = true }
                    )

                    SettingsDivider()

                    SettingsNavigationRow(
                        title = "Response Format",
                        subtitle = "${settings.responseFormat.displayName} — ${settings.responseFormat.description}",
                        testTag = "settings_response_format_row",
                        onClick = { showResponseFormatDialog = true }
                    )

                    SettingsDivider()

                    SettingsToggleRow(
                        title = "Web Search",
                        subtitle = "Request web-grounded answers when available. Availability depends on backend configuration.",
                        checked = settings.webSearchDefault,
                        testTag = "settings_web_search_switch",
                        onCheckedChange = { onSettingsChange(settings.copy(webSearchDefault = it)) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 3: VOICE (Language, Output, Speed)
                SettingsSectionHeader(title = "VOICE", icon = Icons.Default.Mic)
                CardContainer {
                    SettingsNavigationRow(
                        title = "Voice Input Language",
                        subtitle = settings.voiceLanguage,
                        testTag = "settings_voice_language_row",
                        onClick = { showLanguageDialog = true }
                    )

                    SettingsDivider()

                    SettingsToggleRow(
                        title = "Voice Output",
                        subtitle = "Enable read aloud speaker controls on assistant messages",
                        checked = settings.voiceOutputEnabled,
                        testTag = "settings_voice_output_switch",
                        onCheckedChange = { onSettingsChange(settings.copy(voiceOutputEnabled = it)) }
                    )

                    SettingsDivider()

                    SettingsNavigationRow(
                        title = "Speech Speed",
                        subtitle = "${settings.speechSpeed}x" + if (settings.speechSpeed == 1.0f) " (Normal / Default)" else "",
                        testTag = "settings_speech_speed_row",
                        onClick = { showSpeedDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 4: CHAT (Enter to Send, Timestamps, Auto-focus)
                SettingsSectionHeader(title = "CHAT", icon = Icons.AutoMirrored.Filled.Chat)
                CardContainer {
                    SettingsToggleRow(
                        title = "Enter to Send",
                        subtitle = "Send message immediately when pressing Enter",
                        checked = settings.enterToSend,
                        testTag = "settings_enter_to_send_switch",
                        onCheckedChange = { onSettingsChange(settings.copy(enterToSend = it)) }
                    )

                    SettingsDivider()

                    SettingsToggleRow(
                        title = "Show timestamps",
                        subtitle = "Display time sent on message bubbles",
                        checked = settings.showTimestamps,
                        testTag = "settings_show_timestamps_switch",
                        onCheckedChange = { onSettingsChange(settings.copy(showTimestamps = it)) }
                    )

                    SettingsDivider()

                    SettingsToggleRow(
                        title = "Auto-focus composer",
                        subtitle = "Automatically focus composer text field on chat open",
                        checked = settings.autoFocusComposer,
                        testTag = "settings_auto_focus_switch",
                        onCheckedChange = { onSettingsChange(settings.copy(autoFocusComposer = it)) }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 5: ATTACHMENTS (Preview, Max files, Max file size)
                SettingsSectionHeader(title = "ATTACHMENTS", icon = Icons.Default.AttachFile)
                CardContainer {
                    SettingsToggleRow(
                        title = "Show attachment preview",
                        subtitle = "Render rich media thumbnails and document cards in chat",
                        checked = settings.showAttachmentPreview,
                        testTag = "settings_attachment_preview_switch",
                        onCheckedChange = { onSettingsChange(settings.copy(showAttachmentPreview = it)) }
                    )

                    SettingsDivider()

                    SettingsInfoRow(
                        title = "Maximum attachments",
                        value = "5 files per message",
                        subtitle = "Server-enforced limit per message prompt"
                    )

                    SettingsDivider()

                    SettingsInfoRow(
                        title = "Maximum file size",
                        value = "10 MB per file",
                        subtitle = "Supported: PDF, DOCX, TXT, PNG, JPG, WEBP"
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 6: ABOUT
                SettingsSectionHeader(title = "ABOUT", icon = Icons.Default.Info)
                CardContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                    .border(
                                        width = 1.dp,
                                        color = MinovaCyanPrimary.copy(alpha = 0.35f),
                                        shape = RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.minova_icon),
                                    contentDescription = "MINOVA AI Icon",
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "MINOVA AI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "\"Think. Create. Code. With MINOVA.\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MinovaCyanPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Developed by Minhaz",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Version $appVersion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "AI Technology Provider: Google Gemini",
                            style = MaterialTheme.typography.labelSmall,
                            color = MinovaAzureLight
                        )
                    }

                    SettingsDivider()

                    SettingsNavigationRow(
                        title = "Privacy Policy",
                        subtitle = "Local-first storage & Google Cloud security standards",
                        testTag = "settings_privacy_row",
                        onClick = { showPrivacyDialog = true }
                    )

                    SettingsDivider()

                    SettingsNavigationRow(
                        title = "Terms of Service",
                        subtitle = "Responsible AI usage guidelines",
                        testTag = "settings_terms_row",
                        onClick = { showTermsDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 7: RESET (Reset Settings)
                SettingsSectionHeader(title = "RESET", icon = Icons.Default.RestartAlt)
                CardContainer {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showResetConfirmDialog = true }
                            .padding(vertical = 12.dp, horizontal = 4.dp)
                            .testTag("settings_reset_button"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Settings",
                            tint = MinovaError,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Reset Settings",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MinovaError
                            )
                            Text(
                                text = "Restore all preferences to their defaults",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Response Style Dialog
    if (showResponseStyleDialog) {
        AlertDialog(
            onDismissRequest = { showResponseStyleDialog = false },
            title = {
                Text(
                    text = "Response Style",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResponseStyle.entries.forEach { style ->
                        val isSelected = settings.responseStyle == style
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MinovaCyanPrimary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    onSettingsChange(settings.copy(responseStyle = style))
                                    showResponseStyleDialog = false
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSettingsChange(settings.copy(responseStyle = style))
                                    showResponseStyleDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MinovaCyanPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = style.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = style.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResponseStyleDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Response Language Dialog
    if (showResponseLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showResponseLanguageDialog = false },
            title = {
                Text(
                    text = "Response Language",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResponseLanguage.entries.forEach { lang ->
                        val isSelected = settings.responseLanguage == lang
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MinovaCyanPrimary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    onSettingsChange(settings.copy(responseLanguage = lang))
                                    showResponseLanguageDialog = false
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .testTag("response_language_option_${lang.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSettingsChange(settings.copy(responseLanguage = lang))
                                    showResponseLanguageDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MinovaCyanPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = lang.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = lang.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResponseLanguageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Response Format Dialog
    if (showResponseFormatDialog) {
        AlertDialog(
            onDismissRequest = { showResponseFormatDialog = false },
            title = {
                Text(
                    text = "Response Format",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResponseFormat.entries.forEach { format ->
                        val isSelected = settings.responseFormat == format
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) MinovaCyanPrimary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    onSettingsChange(settings.copy(responseFormat = format))
                                    showResponseFormatDialog = false
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .testTag("response_format_option_${format.name.lowercase()}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSettingsChange(settings.copy(responseFormat = format))
                                    showResponseFormatDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MinovaCyanPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = format.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = format.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showResponseFormatDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Voice Language Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Text(
                    text = "Voice Input Language",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    availableLanguages.forEach { lang ->
                        val isSelected = settings.voiceLanguage == lang
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSettingsChange(settings.copy(voiceLanguage = lang))
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSettingsChange(settings.copy(voiceLanguage = lang))
                                    showLanguageDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MinovaCyanPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = lang,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Speech Speed Dialog
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = {
                Text(
                    text = "Speech Speed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    speedOptions.forEach { speed ->
                        val isSelected = settings.speechSpeed == speed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSettingsChange(settings.copy(speechSpeed = speed))
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSettingsChange(settings.copy(speechSpeed = speed))
                                    showSpeedDialog = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = MinovaCyanPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${speed}x" + if (speed == 1.0f) " (Normal / Default)" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Privacy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy") },
            text = {
                Text(
                    "MINOVA AI respects your privacy:\n\n" +
                            "• All chat conversations and history are stored locally on your device via Room SQLite database.\n" +
                            "• Personalization preferences are saved on-device using Android Jetpack DataStore.\n" +
                            "• Attached documents and voice transcripts are processed solely for generating assistant responses via secure Google Gemini API tunnels.\n" +
                            "• No personal telemetry or conversation records are sold or shared with third parties."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Understood")
                }
            }
        )
    }

    // Terms of Service Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms of Service") },
            text = {
                Text(
                    "MINOVA AI Usage Guidelines:\n\n" +
                            "• The assistant provides informational, creative, and technical responses powered by modern generative AI.\n" +
                            "• While highly capable, always review critical code, legal, or health guidance independently.\n" +
                            "• You retain ownership of all documents and prompts you provide to the application."
                )
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Reset Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text(
                    text = "Reset Settings?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MinovaError
                )
            },
            text = {
                Text(
                    "This will restore all preferences (theme, response style, voice options, chat behaviors, and attachment previews) back to their default values.\n\nYour conversations, chat history, and attachment metadata will NOT be deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetSettings()
                        showResetConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MinovaError),
                    modifier = Modifier.testTag("confirm_reset_button")
                ) {
                    Text("Reset to Defaults", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_reset_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MinovaCyanPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun CardContainer(
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun ThemeOptionButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    val bg = if (isSelected) MinovaIndigoSecondary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
    val border = if (isSelected) MinovaCyanPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MinovaCyanPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MinovaCyanPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable { onClick() }
            .padding(vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MinovaCyanPrimary,
                checkedTrackColor = MinovaIndigoSecondary.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .testTag(testTag)
                .semantics {
                    contentDescription = "$title toggle, currently ${if (checked) "enabled" else "disabled"}"
                }
        )
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    value: String,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MinovaAzureLight
        )
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            .padding(vertical = 4.dp)
    )
}
