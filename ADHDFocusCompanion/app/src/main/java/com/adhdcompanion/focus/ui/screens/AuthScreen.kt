package com.adhdcompanion.focus.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.adhdcompanion.focus.data.remote.AuthManager
import com.adhdcompanion.focus.ui.theme.AmoledBlack
import com.adhdcompanion.focus.ui.theme.AmoledOutlineSubtle
import com.adhdcompanion.focus.ui.theme.AmoledSurface
import com.adhdcompanion.focus.ui.theme.FocusCoral
import com.adhdcompanion.focus.ui.theme.FocusCoralContainer
import com.adhdcompanion.focus.ui.theme.FocusCyan
import com.adhdcompanion.focus.ui.theme.FocusEmerald
import com.adhdcompanion.focus.ui.theme.OnAmoled
import com.adhdcompanion.focus.ui.theme.OnAmoledMuted
import com.adhdcompanion.focus.ui.theme.OnAmoledSubtle
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    authManager: AuthManager
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (activity != null) {
            authManager.tryAutoSignIn(activity)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "auth_ring_pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(horizontal = 28.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(116.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val outerRadius = (size.minDimension / 2f) * pulse
                    drawCircle(
                        color = AmoledOutlineSubtle,
                        radius = size.minDimension / 2f - 4.dp.toPx(),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(FocusEmerald, FocusCyan, FocusEmerald)),
                        startAngle = -90f,
                        sweepAngle = 265f,
                        useCenter = false,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawCircle(
                        color = FocusEmerald.copy(alpha = 0.14f),
                        radius = outerRadius * 0.56f
                    )
                }
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(FocusEmerald, shape = androidx.compose.foundation.shape.CircleShape)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Focus Companion",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = OnAmoled,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Calm OLED study timer, app shield & live study halls.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnAmoledMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(30.dp))

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = AmoledSurface,
                border = BorderStroke(0.75.dp, AmoledOutlineSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AuthFeatureRow(
                        icon = Icons.Outlined.Bolt,
                        tint = FocusEmerald,
                        title = "1-tap subject timers & 10-min timeline",
                        subtitle = "Synced across your devices"
                    )
                    AuthFeatureRow(
                        icon = Icons.Outlined.Shield,
                        tint = FocusCyan,
                        title = "App, Shorts & Reels shield",
                        subtitle = "Custom cooldown & exit password"
                    )
                    AuthFeatureRow(
                        icon = Icons.Outlined.Groups,
                        tint = OnAmoled,
                        title = "Real-time study halls",
                        subtitle = "Body-double silently with invite codes"
                    )
                }
            }

            errorMessage?.let { msg ->
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = FocusCoralContainer,
                    border = BorderStroke(0.75.dp, FocusCoral.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = FocusCoral,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    val host = activity ?: return@Button
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        val res = authManager.signInWithGoogle(host)
                        isLoading = false
                        res.onFailure { err ->
                            errorMessage = if (err is GetCredentialCancellationException) {
                                "Sign-in was cancelled. If this happens right after choosing your account, reinstall an APK signed with the registered Firebase keystore."
                            } else {
                                err.message ?: "Google sign-in failed. Please try again."
                            }
                        }
                    }
                },
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnAmoled,
                    contentColor = AmoledBlack
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = AmoledBlack
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Signing in…", fontWeight = FontWeight.SemiBold)
                } else {
                    Text(
                        text = "Continue with Google",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Your study sessions & groups stay backed up to your account.",
                style = MaterialTheme.typography.labelSmall,
                color = OnAmoledSubtle,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AuthFeatureRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = OnAmoled
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnAmoledMuted
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
