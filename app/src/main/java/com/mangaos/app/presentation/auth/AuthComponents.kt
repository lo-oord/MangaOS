package com.mangaos.app.presentation.auth

import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Dimens
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppTopBar

/** The account draft lives with the Activity so every step of the flow shares it. */
@Composable
fun rememberAuthViewModel(): AuthViewModel {
    val activity = LocalContext.current.findComponentActivity()
    return if (activity != null) {
        viewModel(viewModelStoreOwner = activity)
    } else {
        viewModel()
    }
}

private fun android.content.Context.findComponentActivity(): ComponentActivity? {
    var current: android.content.Context? = this
    while (current != null) {
        if (current is ComponentActivity) return current
        current = (current as? android.content.ContextWrapper)?.baseContext
    }
    return null
}

@Composable
fun AuthTopBar(title: String, onBack: () -> Unit) {
    AppTopBar(
        title = title,
        navigationIcon = Icons.AutoMirrored.Outlined.ArrowBack,
        navigationDescription = stringResource(R.string.cd_back),
        onNavigationClick = onBack,
    )
}

@Composable
fun AuthScaffold(title: String, onBack: () -> Unit, content: @Composable (Modifier) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        AuthTopBar(title = title, onBack = onBack)
        content(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.xl),
        )
    }
}

@Composable
fun GoogleButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.buttonHeight)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.google_g),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = stringResource(R.string.signup_with_google),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun FormColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        content()
    }
}
