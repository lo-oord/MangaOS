package com.mangaos.app.presentation.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onFinished: (SplashDestination) -> Unit) {
    val viewModel: SplashViewModel = viewModel()
    var destination by remember { mutableStateOf<SplashDestination?>(null) }

    LaunchedEffect(Unit) {
        viewModel.resolveDestination { destination = it }
    }

    val darkBackground = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val background = if (darkBackground) Color(0xFF0B0E13) else Color.White

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        val travel = with(LocalDensity.current) { -(maxHeight / 2 - 92.dp).toPx() }

        val logoAlpha = remember { Animatable(0f) }
        val logoScale = remember { Animatable(0.9f) }
        val logoOffset = remember { Animatable(0f) }
        val textAlpha = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            launch { logoAlpha.animateTo(1f, tween(360)) }
            logoScale.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
            delay(520)
            logoOffset.animateTo(travel, tween(460, easing = FastOutSlowInEasing))
            delay(90)
            textAlpha.animateTo(1f, tween(320))
            delay(880)
            textAlpha.animateTo(0f, tween(220))
            delay(240)
            while (destination == null) delay(40)
            destination?.let(onFinished)
        }

        Image(
            painter = painterResource(id = R.drawable.logo_mangaos),
            contentDescription = stringResource(id = R.string.cd_logo),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .size(118.dp)
                .graphicsLayer {
                    alpha = logoAlpha.value
                    translationY = logoOffset.value
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 96.dp)
                .graphicsLayer { alpha = textAlpha.value },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(id = R.string.splash_welcome),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (darkBackground) Color(0xFFE7EBF0) else Color(0xFF111820),
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(id = R.string.splash_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = if (darkBackground) Color(0xFF9BA6B5) else Color(0xFF5B6674),
            )
        }
    }
}
