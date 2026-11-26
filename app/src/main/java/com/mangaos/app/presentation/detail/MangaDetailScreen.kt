package com.mangaos.app.presentation.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.ui.components.AppTopBar

/**
 * Route and header only: the detail design arrives with its own specification in phase 2.
 * The screen already receives the manga id and has a working back stack.
 */
@Composable
fun MangaDetailScreen(
    mangaId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MangaDetailViewModel = viewModel()
    LaunchedEffect(mangaId) { viewModel.load(mangaId) }
    val manga by viewModel.manga.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(
            title = manga?.title.orEmpty(),
            navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
            navigationDescription = stringResource(R.string.cd_back),
            onNavigationClick = onBack,
        )
    }
}
