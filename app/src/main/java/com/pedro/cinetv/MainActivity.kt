package com.pedro.cinetv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.pedro.cinetv.data.model.TmdbItem
import com.pedro.cinetv.ui.screens.details.DetailsScreen
import com.pedro.cinetv.ui.screens.home.HomeScreen
import com.pedro.cinetv.ui.screens.search.SearchScreen
import com.pedro.cinetv.ui.theme.CineTvTheme
import com.pedro.cinetv.ui.theme.NeoColors

sealed class Screen {
    object Home : Screen()
    data class Details(val item: TmdbItem) : Screen()
    data class Player(
        val item: TmdbItem,
        val season: Int = 1,
        val episode: Int = 1,
        val episodeTitle: String? = null
    ) : Screen()
    object Search : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CineTvTheme {
                Surface(
                    color = NeoColors.Bg,
                    modifier = Modifier.fillMaxSize()
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                    // D-Pad Back Handler
                    BackHandler(enabled = currentScreen !is Screen.Home) {
                        currentScreen = when (val s = currentScreen) {
                            is Screen.Player -> Screen.Details(s.item)
                            else -> Screen.Home
                        }
                    }

                    when (val screen = currentScreen) {
                        is Screen.Home -> {
                            HomeScreen(
                                onItemSelected = { item ->
                                    currentScreen = Screen.Details(item)
                                },
                                onPlayVideo = { item, season, episode ->
                                    currentScreen = Screen.Player(
                                        item = item,
                                        season = season,
                                        episode = episode
                                    )
                                },
                                onSearchClicked = {
                                    currentScreen = Screen.Search
                                }
                            )
                        }
                        is Screen.Details -> {
                            DetailsScreen(
                                item = screen.item,
                                onBackClicked = {
                                    currentScreen = Screen.Home
                                },
                                onPlayVideo = { season, episode, title ->
                                    currentScreen = Screen.Player(
                                        item = screen.item,
                                        season = season,
                                        episode = episode,
                                        episodeTitle = title
                                    )
                                }
                            )
                        }
                        is Screen.Player -> {
                            com.pedro.cinetv.ui.screens.player.PlayerScreen(
                                item = screen.item,
                                season = screen.season,
                                episode = screen.episode,
                                episodeTitle = screen.episodeTitle,
                                onPlayNextEpisode = { nextSeason, nextEpisode ->
                                    currentScreen = Screen.Player(
                                        item = screen.item,
                                        season = nextSeason,
                                        episode = nextEpisode
                                    )
                                },
                                onBackClicked = {
                                    currentScreen = Screen.Details(screen.item)
                                }
                            )
                        }
                        is Screen.Search -> {
                            SearchScreen(
                                onItemSelected = { item ->
                                    currentScreen = Screen.Details(item)
                                },
                                onBackClicked = {
                                    currentScreen = Screen.Home
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
