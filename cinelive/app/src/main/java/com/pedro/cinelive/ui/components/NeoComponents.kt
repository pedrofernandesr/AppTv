package com.pedro.cinelive.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pedro.cinelive.data.epg.DynamicEpgProvider
import com.pedro.cinelive.data.model.CategoryItem
import com.pedro.cinelive.data.model.ChannelItem
import com.pedro.cinelive.ui.theme.NeoColors
import com.pedro.cinelive.ui.theme.NeoDimens
import com.pedro.cinelive.ui.theme.NeoMotion
import com.pedro.cinelive.ui.theme.NeoType

@Composable
fun NeoCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
            .drawBehind {
                if (isFocused) {
                    drawRect(
                        color = NeoColors.Yellow,
                        topLeft = Offset(NeoDimens.ShadowFocus.toPx(), NeoDimens.ShadowFocus.toPx()),
                        size = size
                    )
                }
            }
            .background(
                if (isFocused) NeoColors.Yellow else if (isSelected) NeoColors.SurfaceFocused else NeoColors.Surface,
                RectangleShape
            )
            .border(
                width = if (isFocused) NeoDimens.BorderFocus else if (isSelected) 2.dp else NeoDimens.BorderRest,
                color = if (isFocused || isSelected) NeoColors.Yellow else NeoColors.BorderRest,
                shape = RectangleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick),
        content = content
    )
}

/**
 * Card de canal utilizado dentro do menu lateral (Drawer) de TV aberta.
 */
@Composable
fun NeoChannelCard(
    channel: ChannelItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    NeoCard(
        onClick = onClick,
        isSelected = isCurrent,
        modifier = modifier
            .fillMaxWidth()
            .height(NeoDimens.ChannelCardH)
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Logotipo do canal em container escuro com contraste
            Box(
                modifier = Modifier
                    .size(56.dp, 44.dp)
                    .background(Color(0xFF161C2C), RectangleShape)
                    .border(1.dp, if (isFocused) NeoColors.Navy else NeoColors.BorderRest, RectangleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(channel.logoUrl)
                            .size(160, 120)
                            .build(),
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = channel.name.take(3).uppercase(),
                        style = NeoType.Badge,
                        color = if (isFocused) NeoColors.Navy else NeoColors.Yellow
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Nome e programa atual
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.name.uppercase(),
                        style = NeoType.CardTitle,
                        color = if (isFocused) NeoColors.Navy else NeoColors.TextPrimary,
                        maxLines = 1
                    )
                    if (isCurrent) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(NeoColors.Red, RectangleShape)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "NO AR",
                                style = NeoType.Badge.copy(color = Color.White)
                            )
                        }
                    }
                }

                val activeEpg = remember(channel) { DynamicEpgProvider.getActiveEpg(channel) }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = activeEpg.title,
                    style = NeoType.EpgTime,
                    color = if (isFocused) NeoColors.Navy.copy(alpha = 0.85f) else NeoColors.TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Pílula / Chip de Categoria para filtragem rápida no menu lateral.
 */
@Composable
fun NeoCategoryChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val (bg, textColor, borderColor) = when {
        isFocused -> Triple(NeoColors.Yellow, NeoColors.Navy, NeoColors.BorderStrong)
        isSelected -> Triple(NeoColors.SurfaceFocused, NeoColors.Yellow, NeoColors.Yellow)
        else -> Triple(NeoColors.Surface, NeoColors.TextSecondary, NeoColors.BorderRest)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(38.dp)
            .drawBehind {
                if (isFocused) {
                    drawRect(
                        color = NeoColors.Yellow,
                        topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                        size = size
                    )
                }
            }
            .background(bg, RectangleShape)
            .border(
                width = if (isFocused || isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RectangleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        Text(
            text = text.uppercase(),
            style = NeoType.Badge,
            color = textColor
        )
    }
}

/**
 * Banner EPG Neo-Brutalista (OSD) que sobrepõe a transmissão ao vivo.
 */
@Composable
fun NeoEpgBanner(
    channel: ChannelItem,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = NeoColors.Yellow,
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = size
                )
            }
            .background(NeoColors.Surface, RectangleShape)
            .border(3.dp, NeoColors.Yellow, RectangleShape)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Logotipo
            if (!channel.logoUrl.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .size(68.dp, 52.dp)
                        .background(Color.Black, RectangleShape)
                        .border(1.dp, NeoColors.BorderRest, RectangleShape)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(channel.logoUrl)
                            .crossfade(100)
                            .build(),
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
            }

            // Metadados do Canal e Programa
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Badge AO VIVO
                    Box(
                        modifier = Modifier
                            .background(NeoColors.Red, RectangleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "[ AO VIVO ]",
                            style = NeoType.Badge.copy(color = Color.White)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = channel.name.uppercase(),
                        style = NeoType.Section,
                        color = NeoColors.Yellow
                    )

                    channel.category?.let { cat ->
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "// $cat".uppercase(),
                            style = NeoType.Badge,
                            color = NeoColors.TextMuted
                        )
                    }
                }

                val activeEpg = remember(channel) { DynamicEpgProvider.getActiveEpg(channel) }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = activeEpg.title,
                    style = NeoType.CardTitle,
                    color = NeoColors.TextPrimary
                )

                Text(
                    text = activeEpg.formattedTime,
                    style = NeoType.EpgTime,
                    color = NeoColors.TextSecondary
                )

                if (!activeEpg.nextTitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val nextSuffix = if (!activeEpg.nextFormattedTime.isNullOrBlank()) " (${activeEpg.nextFormattedTime})" else ""
                    Text(
                        text = "A SEGUIR: ${activeEpg.nextTitle.uppercase()}$nextSuffix",
                        style = NeoType.EpgTime,
                        color = NeoColors.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun NeoBadge(
    text: String,
    modifier: Modifier = Modifier,
    accentColor: Color = NeoColors.TextSecondary
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .border(1.dp, NeoColors.BorderRest, RectangleShape)
            .background(NeoColors.Surface, RectangleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = "[ $text ]",
            style = NeoType.Badge,
            color = accentColor
        )
    }
}

/**
 * Card de canal em formato de grade/catálogo de filmes (tabela visual ampla).
 */
@Composable
fun NeoCatalogCard(
    channel: ChannelItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) NeoMotion.CardScale else 1f,
        animationSpec = tween(NeoMotion.FocusMs),
        label = "neoCatalogCardScale"
    )

    Box(
        modifier = modifier
            .zIndex(if (isFocused) 2f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (isFocused) {
                    drawRect(
                        color = NeoColors.Yellow,
                        topLeft = Offset(NeoDimens.ShadowFocus.toPx(), NeoDimens.ShadowFocus.toPx()),
                        size = size
                    )
                }
            }
            .background(
                if (isFocused) NeoColors.SurfaceFocused else if (isCurrent) NeoColors.Surface else NeoColors.Bg,
                RectangleShape
            )
            .border(
                width = if (isFocused) NeoDimens.BorderFocus else if (isCurrent) 2.dp else NeoDimens.BorderRest,
                color = if (isFocused || isCurrent) NeoColors.Yellow else NeoColors.BorderRest,
                shape = RectangleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Área superior: Logo e Badges em container contrastante
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .background(Color(0xFF161C2C), RectangleShape)
                    .border(1.dp, if (isFocused) NeoColors.Yellow else NeoColors.BorderRest, RectangleShape)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(channel.logoUrl)
                            .size(240, 140)
                            .build(),
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = channel.name.take(4).uppercase(),
                        style = NeoType.Section,
                        color = NeoColors.Yellow
                    )
                }

                // Categoria
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(NeoColors.Bg.copy(alpha = 0.85f), RectangleShape)
                        .border(1.dp, NeoColors.BorderRest, RectangleShape)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = channel.category?.uppercase() ?: "CANAL",
                        style = NeoType.Badge.copy(color = NeoColors.Yellow, fontSize = 9.sp)
                    )
                }

                // Badge NO AR
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(NeoColors.Red, RectangleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NO AR",
                            style = NeoType.Badge.copy(color = Color.White)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Nome do Canal
            Text(
                text = channel.name.uppercase(),
                style = NeoType.CardTitle,
                color = if (isFocused) NeoColors.Yellow else NeoColors.TextPrimary,
                maxLines = 1
            )

            // Programa Atual
            val activeEpg = remember(channel) { DynamicEpgProvider.getActiveEpg(channel) }
            Text(
                text = activeEpg.title,
                style = NeoType.EpgTime,
                color = if (isFocused) NeoColors.TextPrimary else NeoColors.TextSecondary,
                maxLines = 1
            )
        }
    }
}

/**
 * Visualização completa em Grade/Catálogo (tabela de filmes e séries)
 */
@Composable
fun NeoCatalogGridOverlay(
    channels: List<ChannelItem>,
    categories: List<CategoryItem>,
    selectedCategory: CategoryItem?,
    currentChannel: ChannelItem?,
    onCategorySelect: (CategoryItem) -> Unit,
    onChannelSelect: (ChannelItem) -> Unit,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NeoColors.Bg.copy(alpha = 0.98f), RectangleShape)
            .border(4.dp, NeoColors.Yellow, RectangleShape)
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        // Cabeçalho da Grade sem sobreposição de textos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CINELIVE // GRADE COMPLETA DE CANAIS",
                    style = NeoType.Section,
                    color = NeoColors.Yellow
                )
                Text(
                    text = "[ ${channels.size} CANAIS DISPONÍVEIS NA CATEGORIA ]",
                    style = NeoType.Badge,
                    color = NeoColors.TextMuted
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "[ D-PAD: NAVEGAR // OK: ASSISTIR ]",
                    style = NeoType.Badge,
                    color = NeoColors.Yellow
                )
                Spacer(modifier = Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .background(NeoColors.SurfaceFocused, RectangleShape)
                        .border(1.dp, NeoColors.Yellow, RectangleShape)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "[ VOLTAR AO VÍDEO ]",
                        style = NeoType.Badge,
                        color = NeoColors.Yellow
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filtro de Categorias em Linha
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(categories, key = { _, cat -> cat.id }) { index, cat ->
                NeoCategoryChip(
                    text = cat.name,
                    isSelected = cat.id == selectedCategory?.id,
                    onClick = { onCategorySelect(cat) },
                    modifier = if (index == 0 && firstFocusRequester != null) Modifier.focusRequester(firstFocusRequester) else Modifier
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grade de Canais (4 colunas para TV)
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(channels, key = { it.id }) { ch ->
                NeoCatalogCard(
                    channel = ch,
                    isCurrent = ch.id == currentChannel?.id,
                    onClick = { onChannelSelect(ch) },
                    modifier = Modifier.height(160.dp)
                )
            }
        }
    }
}
