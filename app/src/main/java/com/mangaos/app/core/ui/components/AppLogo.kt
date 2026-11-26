package com.mangaos.app.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mangaos.app.R

/** The official MangaOS mark, transparent so it sits on any surface. */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    Image(
        painter = painterResource(id = R.drawable.logo_mangaos),
        contentDescription = stringResource(id = R.string.cd_logo),
        modifier = modifier.size(size),
    )
}
