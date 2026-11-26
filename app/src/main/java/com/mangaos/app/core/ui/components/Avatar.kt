package com.mangaos.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * Avatar used by the drawer and the profile screen. The same fallback is shown for a
 * missing image and for a failed load, so a broken URI never leaves an empty circle.
 */
@Composable
fun ProfileAvatar(
    name: String,
    avatarUri: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    reloadKey: Int = 0,
    onError: () -> Unit = {},
) {
    val context = LocalContext.current
    val model = remember(avatarUri, reloadKey, context) {
        avatarUri?.let { uri ->
            ImageRequest.Builder(context)
                .data(uri)
                .memoryCacheKey("$uri#$reloadKey")
                .build()
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUri != null) {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                loading = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(minOf(size * 0.3f, 28.dp)),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                error = {
                    AvatarInitial(name, size)
                    androidx.compose.runtime.LaunchedEffect(Unit) { onError() }
                },
            )
        } else {
            AvatarInitial(name, size)
        }
    }
}

@Composable
private fun AvatarInitial(name: String, size: Dp) {
    Text(
        text = name.trim().take(1).uppercase().ifBlank { "M" },
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold,
        fontSize = (size.value * 0.38f).sp,
    )
}
