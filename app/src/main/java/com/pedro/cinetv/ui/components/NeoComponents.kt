package com.pedro.cinetv.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.pedro.cinetv.ui.theme.NeoColors
import com.pedro.cinetv.ui.theme.NeoDimens
import com.pedro.cinetv.ui.theme.NeoMotion
import com.pedro.cinetv.ui.theme.NeoType
import kotlin.math.roundToInt

@Composable
fun NeoCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) NeoMotion.PosterScale else 1f,
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
            .background(NeoColors.Surface, RectangleShape)
            .border(
                width = if (isFocused) NeoDimens.BorderFocus else NeoDimens.BorderRest,
                color = if (isFocused) NeoColors.Yellow else NeoColors.BorderRest,
                shape = RectangleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick),
        content = content
    )
}

@Composable
fun NeoPosterCard(
    posterUrl: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeoCard(
        onClick = onClick,
        modifier = modifier
            .width(NeoDimens.PosterW)
            .height(NeoDimens.PosterH)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(posterUrl)
                .crossfade(100)
                .build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun NeoBackdropCard(
    backdropUrl: String,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeoCard(
        onClick = onClick,
        modifier = modifier
            .width(NeoDimens.BackdropW)
            .height(NeoDimens.BackdropH)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(backdropUrl)
                .crossfade(100)
                .build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, NeoColors.Bg.copy(alpha = 0.85f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = title,
                style = NeoType.CardTitle,
                color = NeoColors.TextPrimary,
                maxLines = 1
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = NeoType.Badge,
                    color = NeoColors.TextSecondary
                )
            }
        }
    }
}

/**
 * Card de episódio para séries na tela de detalhes.
 */
@Composable
fun NeoEpisodeCard(
    episodeNumber: Int,
    name: String,
    stillUrl: String,
    runtimeMinutes: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeoCard(
        onClick = onClick,
        modifier = modifier
            .width(220.dp)
            .height(124.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(stillUrl)
                .crossfade(100)
                .build(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, NeoColors.Bg.copy(alpha = 0.92f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = "EP $episodeNumber // ${name.uppercase()}",
                style = NeoType.CardTitle,
                color = NeoColors.TextPrimary,
                maxLines = 1
            )
            if (runtimeMinutes != null && runtimeMinutes > 0) {
                Text(
                    text = "$runtimeMinutes MIN",
                    style = NeoType.Badge,
                    color = NeoColors.Yellow
                )
            }
        }
    }
}

/**
 * Card da fileira 'Continuar Assistindo' com indicador de progresso na base.
 */
@Composable
fun NeoContinueWatchingCard(
    item: com.pedro.cinetv.data.local.ContinueWatchingItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    NeoCard(
        onClick = onClick,
        modifier = modifier
            .width(NeoDimens.BackdropW)
            .height(NeoDimens.BackdropH)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.fullBackdropUrl)
                .crossfade(100)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, NeoColors.Bg.copy(alpha = 0.88f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = item.title,
                style = NeoType.CardTitle,
                color = NeoColors.TextPrimary,
                maxLines = 1
            )
            val sub = if (item.mediaType == "tv") "T${item.season}E${item.episode}" else "FILME"
            Text(
                text = "$sub // CONTINUAR",
                style = NeoType.Badge,
                color = NeoColors.Yellow
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(NeoColors.Surface, RectangleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(item.progressRatio)
                        .background(NeoColors.Yellow, RectangleShape)
                )
            }
        }
    }
}

enum class NeoButtonVariant { Primary, Secondary }

@Composable
fun NeoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NeoButtonVariant = NeoButtonVariant.Primary,
    isSelected: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) NeoMotion.ButtonScale else 1f,
        animationSpec = tween(NeoMotion.FocusMs),
        label = "buttonScale"
    )

    // O preenchimento amarelo Neon é EXCLUSIVO do elemento focado pelo controle remoto.
    // Em repouso, botões primários e selecionados usam fundo escuro com borda e texto amarelos.
    val (bg, textColor, borderColor) = when {
        isFocused -> Triple(NeoColors.Yellow, NeoColors.Navy, NeoColors.BorderStrong)
        isSelected -> Triple(NeoColors.SurfaceFocused, NeoColors.Yellow, NeoColors.Yellow)
        variant == NeoButtonVariant.Primary -> Triple(NeoColors.Surface, NeoColors.Yellow, NeoColors.Yellow)
        else -> Triple(NeoColors.Navy, NeoColors.TextPrimary, NeoColors.BorderRest)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(NeoDimens.ButtonH)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (isFocused) {
                    drawRect(
                        color = NeoColors.Yellow,
                        topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                        size = size
                    )
                }
            }
            .background(bg, RectangleShape)
            .border(
                width = if (isFocused) NeoDimens.BorderFocus else if (variant == NeoButtonVariant.Primary || isSelected) 2.dp else NeoDimens.BorderRest,
                color = borderColor,
                shape = RectangleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = text,
            style = NeoType.Button,
            color = textColor
        )
    }
}

/**
 * Botão minimalista baseado em ícone vetorial (sem emojis) para o player de vídeo.
 * Mantém estética técnica neo-brutalista com dimensões compactas.
 */
@Composable
fun NeoIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: androidx.compose.ui.unit.Dp = 42.dp
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(NeoMotion.FocusMs),
        label = "iconButtonScale"
    )

    val (bg, iconColor, borderColor) = if (isFocused) {
        Triple(NeoColors.Yellow, NeoColors.Navy, NeoColors.BorderStrong)
    } else {
        Triple(NeoColors.Surface, NeoColors.TextPrimary, NeoColors.BorderRest)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(buttonSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (isFocused) {
                    drawRect(
                        color = NeoColors.Yellow,
                        topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                        size = this.size
                    )
                }
            }
            .background(bg, RectangleShape)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RectangleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(buttonSize * 0.55f)
        )
    }
}

/**
 * Pílula minimalista e compacta para seleção de fontes de streaming.
 */
@Composable
fun NeoPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.06f else 1f,
        animationSpec = tween(NeoMotion.FocusMs),
        label = "pillScale"
    )

    val (bg, textColor, borderColor) = when {
        isFocused -> Triple(NeoColors.Yellow, NeoColors.Navy, NeoColors.BorderStrong)
        isSelected -> Triple(NeoColors.SurfaceFocused, NeoColors.Yellow, NeoColors.Yellow)
        else -> Triple(NeoColors.Surface, NeoColors.TextSecondary, NeoColors.BorderRest)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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
            text = text,
            style = NeoType.Badge,
            color = textColor
        )
    }
}

/**
 * Barra de progresso / Seekbar interativa para Smart TV.
 * Permite ao usuário arrastar o progresso do filme usando o controle remoto (D-Pad Esquerda/Direita)
 * com indicador visual (thumb) em destaque e salto direto para o ponto desejado ao clicar OK.
 */
@Composable
fun NeoSeekbar(
    currentPositionMs: Long,
    durationMs: Long,
    scrubPositionMs: Long?,
    onScrub: (Long) -> Unit,
    onSeekCommit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val displayPos = scrubPositionMs ?: currentPositionMs
    val progress = if (durationMs > 0) (displayPos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val trackHeight by animateFloatAsState(
        targetValue = if (isFocused) 8f else 4f,
        animationSpec = tween(NeoMotion.FocusMs),
        label = "trackHeight"
    )

    BoxWithConstraints(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp) // Área focável confortável para controle de TV
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            val current = scrubPositionMs ?: currentPositionMs
                            // Salto inteligente de arraste: ~1% da duração ou 30s
                            val step = (durationMs / 100).coerceIn(15_000L, 60_000L)
                            val newPos = (current - step).coerceAtLeast(0L)
                            onScrub(newPos)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val current = scrubPositionMs ?: currentPositionMs
                            val step = (durationMs / 100).coerceIn(15_000L, 60_000L)
                            val newPos = (current + step).coerceAtMost(durationMs)
                            onScrub(newPos)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER -> {
                            val target = scrubPositionMs ?: currentPositionMs
                            onSeekCommit(target)
                            true
                        }
                        // Deixa DPAD_DOWN e DPAD_UP navegarem entre a seekbar e os botões
                        else -> false
                    }
                } else false
            }
    ) {
        val totalWidth = maxWidth

        // 1. Linha de Fundo (Track de base)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight.dp)
                .background(NeoColors.Surface, RectangleShape)
                .border(
                    width = if (isFocused) 1.dp else 0.dp,
                    color = if (isFocused) NeoColors.BorderStrong else Color.Transparent,
                    shape = RectangleShape
                )
        )

        // 2. Linha Preenchida (Progresso atual/arrastado)
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(trackHeight.dp)
                .background(NeoColors.Yellow, RectangleShape)
        )

        // 3. Indicador de Arraste (Thumb neo-brutalista) visível quando focado
        if (isFocused) {
            val thumbWidth = 14.dp
            val thumbOffset = ((totalWidth - thumbWidth) * progress).coerceAtLeast(0.dp)

            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(width = thumbWidth, height = 22.dp)
                    .drawBehind {
                        drawRect(
                            color = NeoColors.Yellow,
                            topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                            size = this.size
                        )
                    }
                    .background(NeoColors.Yellow, RectangleShape)
                    .border(2.dp, NeoColors.Navy, RectangleShape)
            )
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

@Composable
fun NeoRatingBar(
    voteAverage: Double,
    modifier: Modifier = Modifier
) {
    val filledCount = (voteAverage / 2.0).roundToInt().coerceIn(0, 5)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        for (i in 1..5) {
            val isFilled = i <= filledCount
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(if (isFilled) NeoColors.Yellow else Color.Transparent, RectangleShape)
                    .border(1.dp, NeoColors.Yellow, RectangleShape)
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = String.format("%.1f", voteAverage),
            style = NeoType.Badge,
            color = NeoColors.Yellow
        )
    }
}

@Composable
fun NeoMetadataStripe(
    modifier: Modifier = Modifier,
    stripeColor: Color = NeoColors.Yellow,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(modifier = modifier) {
        Box(
            modifier = Modifier
                .width(NeoDimens.StripeAccent)
                .fillMaxHeight()
                .background(stripeColor, RectangleShape)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(content = content)
    }
}

@Composable
fun NeoSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = NeoType.Section,
        color = NeoColors.TextPrimary,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun NeoSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (isFocused) NeoColors.SurfaceFocused else NeoColors.Surface, RectangleShape)
            .border(
                width = if (isFocused) NeoDimens.BorderFocus else NeoDimens.BorderRest,
                color = if (isFocused) NeoColors.Yellow else NeoColors.BorderRest,
                shape = RectangleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "> BUSCAR: ",
            style = NeoType.Button,
            color = if (isFocused) NeoColors.Yellow else NeoColors.TextSecondary
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = NeoType.Body.copy(color = NeoColors.TextPrimary),
            cursorBrush = SolidColor(NeoColors.Yellow),
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        if (query.isNotEmpty()) {
            Text(
                text = "[ LIMPAR ]",
                style = NeoType.Badge,
                color = NeoColors.TextMuted,
                modifier = Modifier.clickable { onQueryChange("") }
            )
        }
    }
}
