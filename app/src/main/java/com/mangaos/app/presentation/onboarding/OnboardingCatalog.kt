package com.mangaos.app.presentation.onboarding

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.ui.graphics.vector.ImageVector
import com.mangaos.app.R
import com.mangaos.app.core.designsystem.AppTheme
import com.mangaos.app.core.designsystem.MangaOsIcons

/** One selectable genre: stable id, localized label and its icon in the shared vocabulary. */
data class GenreOption(
    val id: String,
    @StringRes val label: Int,
    val icon: ImageVector,
)

data class ContentTypeOption(
    val id: String,
    @StringRes val label: Int,
    val icon: ImageVector,
)

/** The genres the wizard offers, in the order the product specification lists them. */
val genreOptions: List<GenreOption> = listOf(
    GenreOption("action", R.string.genre_action, MangaOsIcons.genreAction),
    GenreOption("adventure", R.string.genre_adventure, MangaOsIcons.genreAdventure),
    GenreOption("romance", R.string.genre_romance, MangaOsIcons.genreRomance),
    GenreOption("fantasy", R.string.genre_fantasy, MangaOsIcons.genreFantasy),
    GenreOption("horror", R.string.genre_horror, MangaOsIcons.genreHorror),
    GenreOption("comedy", R.string.genre_comedy, MangaOsIcons.genreComedy),
    GenreOption("drama", R.string.genre_drama, MangaOsIcons.genreDrama),
    GenreOption("psychological", R.string.genre_psychological, MangaOsIcons.genrePsychological),
    GenreOption("thriller", R.string.genre_thriller, MangaOsIcons.genreThriller),
    GenreOption("sports", R.string.genre_sports, MangaOsIcons.genreSports),
    GenreOption("school", R.string.genre_school, MangaOsIcons.genreSchool),
    GenreOption("historical", R.string.genre_historical, MangaOsIcons.genreHistorical),
    GenreOption("sci_fi", R.string.genre_sci_fi, MangaOsIcons.genreSciFi),
    GenreOption("mystery", R.string.genre_mystery, MangaOsIcons.genreMystery),
    GenreOption("slice_of_life", R.string.genre_slice_of_life, MangaOsIcons.genreSliceOfLife),
)

/** Only the work types the bundled sources actually publish and the app can read. */
val contentTypeOptions: List<ContentTypeOption> = listOf(
    ContentTypeOption("manga", R.string.content_type_manga, Icons.Outlined.MenuBook),
    ContentTypeOption("manhwa", R.string.content_type_manhwa, Icons.Outlined.AutoStories),
    ContentTypeOption("manhua", R.string.content_type_manhua, Icons.Outlined.CollectionsBookmark),
)

@StringRes
fun themeTitle(theme: AppTheme): Int = when (theme) {
    AppTheme.DEFAULT -> R.string.theme_default
    AppTheme.SAKURA -> R.string.theme_sakura
    AppTheme.SHANA -> R.string.theme_shana
    AppTheme.MIKU -> R.string.theme_miku
    AppTheme.NEON -> R.string.theme_neon
    AppTheme.SKY -> R.string.theme_sky
    AppTheme.FOREST -> R.string.theme_forest
    AppTheme.COFFEE -> R.string.theme_coffee
}
