package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.MinovaAzureLight
import com.example.ui.theme.MinovaCyanPrimary
import com.example.ui.theme.MinovaDarkSurface
import com.example.ui.theme.MinovaVioletAccent
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale = remember { Animatable(0.90f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650)
        )
        // Automatic advance after splash introduction
        delay(1400)
        onContinue()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .scale(scale.value)
                .alpha(alpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Framed Geometric Brand Lockup (Emblem + MINOVA AI Wordmark)
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MinovaDarkSurface,
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MinovaCyanPrimary.copy(alpha = 0.45f),
                                MinovaVioletAccent.copy(alpha = 0.35f)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.minova_logo),
                    contentDescription = "MINOVA AI Logo",
                    modifier = Modifier
                        .size(width = 220.dp, height = 64.dp)
                        .testTag("splash_logo")
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Think. Create. Code.",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 0.6.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "One Intelligence. Infinite Possibilities.",
                style = MaterialTheme.typography.bodyMedium,
                color = MinovaCyanPrimary,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Product identity pill
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MinovaVioletAccent.copy(alpha = 0.12f))
                    .border(
                        width = 1.dp,
                        color = MinovaVioletAccent.copy(alpha = 0.35f),
                        shape = CircleShape
                    )
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MinovaAzureLight)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MINOVA AI • Developed by Minhaz",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(42.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth(0.76f)
                    .height(50.dp)
                    .testTag("splash_continue_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MinovaCyanPrimary,
                    contentColor = Color(0xFF04131E)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Launch Assistant",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
