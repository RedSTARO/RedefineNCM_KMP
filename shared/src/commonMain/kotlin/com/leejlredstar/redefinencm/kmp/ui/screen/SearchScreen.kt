package com.leejlredstar.redefinencm.kmp.ui.screen

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.leejlredstar.redefinencm.kmp.ui.icon.AppIcons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.leejlredstar.redefinencm.kmp.player.PlatformPlayer
import com.leejlredstar.redefinencm.kmp.ui.component.connectedListItemShape
import com.leejlredstar.redefinencm.kmp.ui.theme.contentAccentPalette
import com.leejlredstar.redefinencm.kmp.util.PlatformSettings
import com.leejlredstar.redefinencm.kmp.util.SettingKeys
import com.leejlredstar.redefinencm.kmp.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: MainViewModel = koinInject(),
    player: PlatformPlayer = koinInject(),
    settings: PlatformSettings = koinInject(),
) {
    val results by viewModel.searchResults.collectAsState()
    val suggestions by viewModel.searchSuggestions.collectAsState()
    val loading by viewModel.searchLoading.collectAsState()
    var query by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val searchPrediction = remember { settings.getBoolean(SettingKeys.SEARCH_PREDICTION, true) }
    val searchPalette = contentAccentPalette(MaterialTheme.colorScheme.tertiaryContainer)

    LaunchedEffect(Unit) { viewModel.clearSearch() }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            viewModel.clearSearch()
        } else if (searchPrediction) {
            delay(300)
            viewModel.fetchSearchSuggestions(query)
        }
    }

    fun submit(text: String) {
        query = text
        keyboard?.hide()
        viewModel.search(text)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        searchPalette.pageStart,
                        searchPalette.pageMiddle,
                        searchPalette.pageEnd,
                    ),
                ),
            )
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Surface(
                    shape = CircleShape,
                    color = searchPalette.quietContainer,
                    contentColor = searchPalette.onQuietContainer,
                ) {
                    Icon(
                        AppIcons.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            with(sharedTransitionScope) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索歌曲、歌手") },
                    singleLine = true,
                    leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; viewModel.clearSearch() }) {
                                Icon(AppIcons.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                    shape = CircleShape,
                    modifier = Modifier
                        .sharedBounds(
                            rememberSharedContentState(SharedKeys.search()),
                            animatedVisibilityScope,
                        )
                        .fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = searchPalette.quietContainer,
                        unfocusedContainerColor = searchPalette.quietContainer,
                        focusedTextColor = searchPalette.onQuietContainer,
                        unfocusedTextColor = searchPalette.onQuietContainer,
                        focusedLeadingIconColor = searchPalette.accent,
                        unfocusedLeadingIconColor = searchPalette.onQuietContainer.copy(alpha = 0.72f),
                        focusedTrailingIconColor = searchPalette.onQuietContainer,
                        unfocusedTrailingIconColor = searchPalette.onQuietContainer,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = searchPalette.quietContainer,
                        contentColor = searchPalette.accent,
                    ) {
                        CircularProgressIndicator(Modifier.padding(28.dp))
                    }
                }
            }
            results.isEmpty() && searchPrediction && suggestions.isNotEmpty() -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(suggestions) { index, keyword ->
                        Surface(
                            onClick = { submit(keyword) },
                            shape = connectedListItemShape(index, suggestions.size),
                            color = searchPalette.quietContainer,
                            contentColor = searchPalette.onQuietContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.5.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    AppIcons.Search,
                                    contentDescription = null,
                                    tint = searchPalette.accent,
                                )
                                Spacer(Modifier.size(12.dp))
                                Text(text = keyword, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            results.isNotEmpty() -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(results) { index, song ->
                        SongRow(
                            index = index,
                            title = song.name,
                            artist = song.ar.joinToString(" / ") { it.name }.ifEmpty { "未知歌手" },
                            artworkUri = song.al.picUrl,
                            shape = connectedListItemShape(index, results.size),
                            onClick = {
                                player.setQueue(listOf(song.toMediaInfo()), 0)
                                player.play()
                                onBack()
                            },
                            songId = song.id,
                        )
                    }
                }
            }
            query.isNotBlank() -> {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = searchPalette.quietContainer,
                    contentColor = searchPalette.onQuietContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "按搜索键查找 \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }
        }
    }
}
