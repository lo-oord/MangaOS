package com.mangaos.app.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppLogo
import com.mangaos.app.core.ui.components.PrimaryButton
import com.mangaos.app.core.ui.components.SecondaryButton

@Composable
fun AccountChoiceScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onGuest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        AppLogo(size = 96.dp)
        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            PrimaryButton(
                text = stringResource(R.string.auth_create_account),
                onClick = onCreateAccount,
            )
            SecondaryButton(
                text = stringResource(R.string.auth_existing_user),
                onClick = onSignIn,
            )
            SecondaryButton(
                text = stringResource(R.string.auth_guest),
                onClick = onGuest,
            )
        }

        Spacer(Modifier.height(40.dp))
    }
}
