package com.mangaos.app.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.Dimens
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.domain.model.SessionType
import com.mangaos.app.domain.model.UserProfile
import com.mangaos.app.presentation.navigation.DrawerDestination

@Composable
fun AppDrawer(
    profile: UserProfile?,
    unreadNotifications: Int,
    currentRoute: String?,
    onNavigate: (DrawerDestination) -> Unit,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerShape = RoundedCornerShape(20.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(vertical = Spacing.sm),
        ) {
            DrawerHeader(
                profile = profile,
                unreadNotifications = unreadNotifications,
                onNotificationsClick = onNotificationsClick,
                onProfileClick = onProfileClick,
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            DrawerDestination.entries.forEach { destination ->
                if (destination == DrawerDestination.SETTINGS) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xs),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                DrawerItem(
                    destination = destination,
                    selected = currentRoute == destination.route,
                    onClick = { onNavigate(destination) },
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun DrawerHeader(
    profile: UserProfile?,
    unreadNotifications: Int,
    onNotificationsClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    val isGuest = profile?.sessionType == SessionType.GUEST
    val displayName = when {
        profile == null -> ""
        isGuest -> stringResource(R.string.guest_display_name)
        else -> profile.displayName
    }
    val username = profile?.username ?: stringResource(R.string.guest_username)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.md, bottom = Spacing.xs),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onProfileClick)
                .padding(vertical = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileAvatar(
                name = displayName.ifBlank { username },
                avatarUri = profile?.avatarUri,
                size = 64.dp,
            )
            Spacer(Modifier.height(Spacing.sm))
            // The badge and the gold name follow the account: a verified reader is recognised
            // in the drawer exactly as on the profile page.
            VerifiedName(
                name = "@$username",
                verified = profile?.verified == true,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                badgeSize = 13.dp,
                showBadge = !isGuest,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (profile?.verified == true) {
                    com.mangaos.app.core.designsystem.goldColor()
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AppIconButton(
            icon = Icons.Outlined.NotificationsNone,
            contentDescription = stringResource(R.string.cd_notifications),
            onClick = onNotificationsClick,
            badgeCount = unreadNotifications,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = Spacing.sm),
        )
    }
}

@Composable
private fun DrawerItem(
    destination: DrawerDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Text(
                text = stringResource(destination.labelRes),
                style = MaterialTheme.typography.titleSmall,
            )
        },
        icon = {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.padding(horizontal = Spacing.md, vertical = 1.dp),
    )
}
