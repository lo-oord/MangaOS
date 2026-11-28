package com.mangaos.app.presentation.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The launch screen: the mark, nothing else. Settings, language, theme, the wizard state and the
 * session are all read in the background while it plays; as soon as both the entrance animation
 * and that read are done it leaves, without a second of dead time or an intermediate screen.
 */
@Composable
fun SplashScreen(onFinished: (SplashDestination) -> Unit) {
    val viewModel: SplashViewModel = viewModel()
    var destination by remember { mutableStateOf<SplashDestination?>(null) }
    val reduceMotion = LocalReduceMotion.current

    LaunchedEffect(Unit) {
        viewModel.resolveDestination { resolved -> destination = resolved }
    }

    val logoAlpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val logoScale = remember { Animatable(if (reduceMotion) 1f else 0.9f) }
    val wordmarkAlpha = remember { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!reduceMotion) {
            launch { logoAlpha.animateTo(1f, tween(200)) }
            logoScale.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            delay(120)
            wordmarkAlpha.animateTo(1f, tween(240))
            delay(120)
        } else {
            delay(160)
        }
        while (destination == null) delay(20)
        destination?.let(onFinished)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        // A single, very soft accent behind the mark; no text, no copy to read.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0f),
                        ),
                    ),
                ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.logo_mangaos),
                contentDescription = stringResource(id = R.string.cd_logo),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    },
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                modifier = Modifier.graphicsLayer { alpha = wordmarkAlpha.value },
            )
        }
    }
}
