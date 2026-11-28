package com.mangaos.app.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.AppIcon
import com.mangaos.app.core.designsystem.IconRole
import com.mangaos.app.core.designsystem.MangaOsIcons
import com.mangaos.app.core.designsystem.Spacing
import com.mangaos.app.core.ui.components.AppBottomSheet
import com.mangaos.app.domain.model.SourceInfo

/**
 * Compact enabled-source picker. Every source is one round-ish bubble carrying its real icon and
 * its name, wrapped into rows instead of a long list, so the sheet stays small and the screen
 * underneath stays visible. There is no "every source" bubble: one source is always chosen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourcePickerSheet(
    sources: List<SourceInfo>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.source_pick_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.size(Spacing.sm))
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                sources.forEach { source ->
                    SourceBubble(
                        name = source.name,
                        iconRes = source.iconRes,
                        selected = source.id == selectedId,
                        onClick = { onSelect(source.id) },
                    )
                }
            }
            TextButton(onClick = onManage, modifier = Modifier.padding(top = Spacing.xs)) {
                Text(
                    text = stringResource(R.string.source_manage),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SourceBubble(
    name: String,
    iconRes: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (selected) scheme.primary.copy(alpha = 0.55f) else scheme.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(scheme.surface.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                if (iconRes != null) {
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    // One neutral glyph for every server; no brand artwork is shown anywhere.
                    AppIcon(
                        icon = MangaOsIcons.sources,
                        contentDescription = null,
                        role = if (selected) IconRole.Primary else IconRole.Muted,
                        size = 15.dp,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 132.dp),
            )
            if (selected) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
