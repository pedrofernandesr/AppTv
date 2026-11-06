package com.pedro.cinelive.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    val scale by animateFloatAsState(
        targetValue = if (isFocused) NeoMotion.CardScale else 1f,
        animationSpec = tween(NeoMotion.FocusMs),
        label = "neoCardScale"
    )

    Box(
        modifier = modifier
            .zIndex(if (isFocused) 1f else 0f)
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
                if (isFocused) NeoColors.SurfaceFocused else if (isSelected) NeoColors.Surface else NeoColors.Bg,
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
            // Logotipo do canal
            Box(
                modifier = Modifier
                    .size(52.dp, 40.dp)
                    .background(Color.Black, RectangleShape)
                    .border(1.dp, if (isFocused) NeoColors.Yellow else NeoColors.BorderRest, RectangleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(channel.logoUrl)
                            .crossfade(100)
                            .build(),
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = channel.name.take(3).uppercase(),
                        style = NeoType.Badge,
                        color = NeoColors.Yellow
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
                        color = if (isFocused) NeoColors.Yellow else NeoColors.TextPrimary,
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

                val currentShow = channel.epg?.current?.title
                if (!currentShow.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentShow,
                        style = NeoType.EpgTime,
                        color = NeoColors.TextSecondary,
                        maxLines = 1
                    )
                }
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

                val currentProgram = channel.epg?.current
                val nextProgram = channel.epg?.next

                if (currentProgram != null && !currentProgram.title.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentProgram.title,
                        style = NeoType.CardTitle,
                        color = NeoColors.TextPrimary
                    )

                    if (!currentProgram.formattedTime.isNullOrBlank()) {
                        Text(
                            text = currentProgram.formattedTime,
                            style = NeoType.EpgTime,
                            color = NeoColors.TextSecondary
                        )
                    }
                }

                if (nextProgram != null && !nextProgram.title.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "A SEGUIR: ${nextProgram.title.uppercase()}",
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
