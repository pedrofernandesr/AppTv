package com.pedro.cinetv.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.pedro.cinetv.data.api.TmdbRepository
import com.pedro.cinetv.data.model.TmdbItem
import com.pedro.cinetv.ui.components.NeoButton
import com.pedro.cinetv.ui.components.NeoButtonVariant
import com.pedro.cinetv.ui.components.NeoPosterCard
import com.pedro.cinetv.ui.components.NeoSearchField
import com.pedro.cinetv.ui.theme.NeoColors
import com.pedro.cinetv.ui.theme.NeoDimens
import com.pedro.cinetv.ui.theme.NeoType
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    onItemSelected: (TmdbItem) -> Unit,
    onBackClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TmdbItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val repository = remember { TmdbRepository() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            isLoading = true
            results = repository.search(query)
            isLoading = false
        } else {
            results = emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoColors.Bg)
            .padding(horizontal = NeoDimens.SafeH, vertical = NeoDimens.SafeV)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            NeoButton(
                text = "[ VOLTAR ]",
                onClick = onBackClicked,
                variant = NeoButtonVariant.Secondary
            )
            NeoSearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                CircularProgressIndicator(color = NeoColors.Yellow)
            }
        } else if (query.isBlank()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, NeoColors.BorderRest, RectangleShape)
                    .background(NeoColors.Surface, RectangleShape)
            ) {
                Text(
                    text = "DIGITE O NOME DE UM FILME OU SÉRIE PARA BUSCAR // TMDB",
                    style = NeoType.Badge,
                    color = NeoColors.TextMuted
                )
            }
        } else if (results.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, NeoColors.BorderRest, RectangleShape)
                    .background(NeoColors.Surface, RectangleShape)
            ) {
                Text(
                    text = "NENHUM RESULTADO ENCONTRADO PARA O TERMO: \"$query\"",
                    style = NeoType.Badge,
                    color = NeoColors.TextMuted
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = NeoDimens.PosterW),
                horizontalArrangement = Arrangement.spacedBy(NeoDimens.Gap),
                verticalArrangement = Arrangement.spacedBy(NeoDimens.Gap),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(results, key = { it.id }) { item ->
                    NeoPosterCard(
                        posterUrl = item.fullPosterUrl,
                        title = item.displayTitle,
                        onClick = { onItemSelected(item) }
                    )
                }
            }
        }
    }
}
