# ESTILO.md — CineTV (Android TV)
# Guia Visual e Especificação Técnica Definitiva

Documento de especificação de design e engenharia de interface para o aplicativo **CineTV** (Android TV), desenvolvido em **Kotlin + Jetpack Compose for TV**.

A identidade visual é um **Neo-Brutalismo Técnico** rigoroso, derivado da síntese entre os projetos do Pedro:
- **Portfólio Pessoal** (neo-brutalismo, estética terminal CLI, monoespaçado, blocos sólidos de cor, contraste seco)
- **PotiHub / Ledger UI** (sistema técnico utilitário, paleta escura profunda, amarelo acento de ação `#D5E022`, fichas telegráficas)

> [!IMPORTANT]
> **Regra Suprema de Engenharia:** Em qualquer eventual conflito entre fidelidade estética e usabilidade, a **navegação previsível por D-pad, a legibilidade imediata a 3 metros de distância e o consumo ultraleve de memória em processadores de Smart TV** prevalecem absolutamente.

---

## 0. Calibração Real da Tela (Hardware Alvo)

O hardware alvo foi calibrado diretamente via ADB (`platform-tools/adb.exe` no IP `192.168.1.53:5555`):
- **Dispositivo:** Smart TV TCL (Android 11 / API 30)
- **Tamanho Físico (`wm size`):** `1280x720` pixels
- **Densidade Física (`wm density`):** `213 dpi` (`DENSITY_TV`)
- **Cálculo da Escala Lógica:**
  $$\text{Largura em dp} = 1280 \div \left(\frac{213}{160}\right) = 1280 \div 1.33125 \approx \mathbf{961.5\,dp}$$
  $$\text{Altura em dp} = 720 \div \left(\frac{213}{160}\right) = 720 \div 1.33125 \approx \mathbf{540.8\,dp}$$

**Conclusão de Calibração:** A tela opera na base canônica **960 x 540 dp** (escala x1.33 de 720p). Todas as medidas e proporções deste documento utilizam esta base 1:1.

---

## 1. Identidade e Filosofia Neo-Brutalista

- **Estilo:** Neo-brutalismo técnico / terminal de cinema / ficha técnica utilitária. O aplicativo funciona como um instrumento de precisão de alta performance: catálogo direto (TMDB) e streaming integrado (MegaEmbed), sem firulas, sem popups invasivos e sem poluição visual.
- **Clima:** Sóbrio, mecânico, tátil, confiável, focado no conteúdo com contraste cirúrgico.
- **Público:** Cinéfilos, desenvolvedores e usuários de TV que valorizam velocidade, navegação por controle remoto fluida a 60 fps e estética minimalista com personalidade.
- **Cinco Adjetivos Fundamentais:**
  1. **Rígido** (sem curvas, ângulos retos de 90° em 100% dos elementos)
  2. **Monoespaçado** (tipografia alinhada, estética de terminal de dados)
  3. **Tátil** (sensação mecânica de clique, sombras duras projetadas de 6dp)
  4. **Alto-contraste** (fundo quase preto `#0A0A1A`, elementos nítidos, zero tons pastéis desbotados)
  5. **Telegráfico** (copywriting curto, direto, em caixa alta nos controles, dados técnicos entre colchetes)

### O que o Design NUNCA faz
- **NUNCA** usa cantos arredondados (`RoundedCornerShape` é terminantemente proibido; sempre `RectangleShape`).
- **NUNCA** usa sombras com desfoque gaussiano (`blur` ou `Modifier.shadow()`).
- **NUNCA** usa efeitos de vidro (`glassmorphism`, `backdrop-filter` ou `RenderEffect`).
- **NUNCA** usa gradientes decorativos multicoloridos (gradiente só existe em dois lugares: fade vertical seco no Hero e gradiente sutil no rodapé do player).
- **NUNCA** usa tipografia sans-serif variável decorativa (apenas monoespaçada).
- **NUNCA** usa animações com mola ou elasticidade (`spring`, `bounce` ou overshoot).
- **NUNCA** usa branco puro (`#FFFFFF`) sobre o amarelo de foco (texto sobre amarelo é sempre Navy `#170E4A`).
- **NUNCA** usa emojis como ícones de botões (ícones devem ser vetoriais puros de linha ou caracteres tipográficos ASCII).

---

## 2. Paleta de Cores e Regras Rígidas

### 2.1 Tabela de Cores (Hexadecimal & Alpha)

| Token Kotlin | Hex / ARGB | Função no Design | Regra Estrita de Uso |
| :-- | :-- | :-- | :-- |
| `NeoColors.Bg` | `#FF0A0A1A` | Fundo principal de todas as telas | Fundo imersivo quase preto; nunca clarear |
| `NeoColors.Surface` | `#FF1A1A35` | Fundo dos cards em repouso e painéis | Superfície base para pôsteres e blocos |
| `NeoColors.SurfaceFocused` | `#FF24196B` | Fundo de botões e cards no ganho de foco | Aumenta o contraste interno no foco |
| `NeoColors.Yellow` | `#FFD5E022` | Cor de destaque, borda de foco e botão CTA | Foco D-pad ativo e botões primários |
| `NeoColors.YellowPressed` | `#FFC2CE1F` | Estado pressionado do botão primário | Feedback mecânico ao clicar com D-pad Center |
| `NeoColors.Navy` | `#FF170E4A` | Texto e ícones sobre amarelo | **Obrigatório** sempre que o fundo for `Yellow` |
| `NeoColors.TextPrimary` | `#FFF4F4F0` | Títulos, rótulos principais e textos ativos | Off-white anti-fadiga visual; nunca branco puro |
| `NeoColors.TextSecondary` | `#B3F4F4F0` (70%) | Sinopses, diretores e metadados | Alto contraste para leitura a 3 metros |
| `NeoColors.TextMuted` | `#80F4F4F0` (50%) | Rótulos de tags desativadas e estados vazios | Informações secundárias e molduras |
| `NeoColors.BorderRest` | `#59F4F4F0` (35%) | Borda de 1-2dp dos cards em repouso | Delimitação sutil sem poluir visualmente |
| `NeoColors.BorderStrong` | `#FFF4F4F0` | Borda de 3dp de foco do botão primário | Contorno de reforço tátil |
| `NeoColors.Red` | `#FFED1A25` | Erro, transmissões ao vivo e alertas | Falhas de conexão, botões de ação destrutiva |
| `NeoColors.Green` | `#FF22C55E` | Sucesso, status "Disponível" | Disponibilidade confirmada |
| `NeoColors.Blue` | `#FF3B82F6` | Status "Continuar Assistindo" | Progresso de reprodução recente |
| `NeoColors.Slate` | `#FF64748B` | Status "Já Assistido" | Títulos marcados como concluídos |

### 2.2 Regras de Aplicação do Amarelo (`#D5E022`)
- O preenchimento com fundo amarelo é **privilégio exclusivo do elemento focado** no momento pelo controle remoto.
- Em repouso, botões primários usam contorno e texto amarelos sobre superfície escura, para evitar que a tela pareça ter múltiplos focos simultâneos.

---

## 3. Tipografia (Monoespaçada / JetBrains Mono)

A tipografia deve utilizar a família monoespaçada (renderização nativa de terminal):
- Fonte: `FontFamily.Monospace` (ou `jetbrains_mono_*.ttf` em `res/font/`)
- Pesos: `FontWeight.Normal` (400), `FontWeight.Bold` (700) e `FontWeight.ExtraBold` (800).

> [!CAUTION]
> **Regra dos 14sp:** O olho humano a 3 metros de distância não consegue ler confortavelmente fontes menores que 14sp em TVs convencionais. **Nenhum elemento da interface pode ter tamanho inferior a 14sp.**

### Escala Tipográfica Formal

| Estilo (`NeoType`) | Tamanho | Altura de Linha | Peso | Espaçamento | Capitalização / Aplicação |
| :-- | :-- | :-- | :-- | :-- | :-- |
| `Hero` | 40sp | 44sp | ExtraBold | -0.5sp | Título do filme no Hero Banner (máx 2 linhas) |
| `Section` | 24sp | 28sp | ExtraBold | +0.5sp | Títulos das fileiras (`EM ALTA // SEMANA`) |
| `CardTitle` | 18sp | 22sp | Bold | 0sp | Título nos cards de backdrop ou diálogos |
| `Body` | 16sp | 24sp | Normal | 0sp | Sinopses, fichas técnicas e descrições |
| `Button` | 16sp | 20sp | Bold | +1.0sp | Caixa alta: `[ ASSISTIR AGORA ]`, `[ DETALHES ]` |
| `Badge` | 14sp | 16sp | Bold | +1.5sp | Selos entre colchetes: `[2026]`, `[16]`, `[4K]` |

---

## 4. Espaçamento, Grid e Safe Area de TV

### 4.1 Safe Area (Overscan de Televisão)
- Margem Horizontal de Segurança: **`48.dp`** (esquerda e direita)
- Margem Vertical de Segurança: **`27.dp`** (topo e rodapé)
- Nenhum componente interativo ou informação textual crítica ultrapassa a Safe Area.

### 4.2 Proporções e Dimensões de Componentes
- **Pôster de Filme (2:3):** `132.dp` de largura x `198.dp` de altura
- **Backdrop de Destaque / Continuar (16:9):** `240.dp` de largura x `135.dp` de altura
- **Card de Episódio de Série (16:9 estendido):** `260.dp` de largura x `150.dp` de altura
- **Altura Mínima de Foco (D-pad touch target):** `42.dp` a `48.dp`
- **Gap Horizontal entre Cards:** `16.dp`
- **Gap Vertical entre Fileiras:** `24.dp`
- **Faixa Lateral de Metadados:** `4.dp` de largura + `12.dp` de recuo à esquerda

---

## 5. Formas, Bordas e Profundidade (Neo-Brutalismo)

1. **Raios de Canto:** Rigorosamente `0.dp` (`RectangleShape`) em toda a árvore de componentes.
2. **Borda de Repouso:** `2.dp` sólida com a cor `BorderRest` (`#59F4F4F0`).
3. **Borda em Foco:** `3.dp` sólida com a cor `Yellow` (`#D5E022`).
4. **Sombra Dura de Foco (Hard Shadow):**
   - Zero desfoque (`blur = 0`).
   - Deslocamento fixo: `6.dp` no eixo X e `6.dp` no eixo Y (`Offset(6.dp.toPx(), 6.dp.toPx())`).
   - Cor da sombra: `Yellow` (`#D5E022`).
   - Implementação via `Modifier.drawBehind { drawRect(...) }`.
5. **Elevação e Escala no Foco:**
   - Cards de pôster aumentam a escala em `1.08x` (`graphicsLayer { scaleX = 1.08f; scaleY = 1.08f }`).
   - Botões aumentam a escala em `1.04x`.
   - Z-Index sobe para `1f` no elemento focado (`Modifier.zIndex(1f)`), garantindo que o card sobreponha os vizinhos da fileira.
   - **Regra de Padding:** O container `LazyRow` deve obrigatoriamente possuir `contentPadding = PaddingValues(vertical = 16.dp, horizontal = 48.dp)` para que a projeção da sombra e a escala de 1.08x nunca sejam cortadas pela borda da lista.

---

## 6. Catálogo Completo de Componentes (Código Jetpack Compose for TV)

### 6.1 Card Base Neo-Brutalista (`NeoCard`)
Base reutilizável para todos os cards do catálogo:

```kotlin
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
```

### 6.2 Card de Pôster (`NeoPosterCard`)
Proporção 2:3 (`132.dp` x `198.dp`), carregamento otimizado via Coil:

```kotlin
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
```

### 6.3 Card de Destaque / Backdrop (`NeoBackdropCard`)
Proporção 16:9 (`240.dp` x `135.dp`) com gradiente vertical escuro para legibilidade do título:

```kotlin
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
```

### 6.4 Card de Continuar Assistindo (`NeoContinueWatchingCard`)
Exibe o progresso em tempo real gravado no storage local com barra amarela na base e identificação técnica de episódio (`T1:E3 // CONTINUAR`):

```kotlin
@Composable
fun NeoContinueWatchingCard(
    item: ContinueWatchingItem,
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
```

### 6.5 Card de Episódio de Séries (`NeoEpisodeCard`)
Dimensão `260.dp` x `150.dp`, exibindo thumbnail de cena TMDB, número do episódio e duração em minutos:

```kotlin
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
            .width(260.dp)
            .height(150.dp)
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
                .padding(10.dp)
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
```

### 6.6 Botão Neo-Brutalista (`NeoButton`)
Preenchimento amarelo exclusivo de foco; em repouso contorno amarelo 2dp:

```kotlin
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
```

### 6.7 Botão de Ícone Minimalista (`NeoIconButton`)
Compacto (`42.dp`), sem emojis, com ícones vetoriais de linha (Material Icons):

```kotlin
@Composable
fun NeoIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 42.dp
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
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(buttonSize * 0.55f)
        )
    }
}
```

### 6.8 Pílula de Filtro e Fonte (`NeoPillButton`)
Utilizado na barra de navegação superior (`[ INÍCIO ]`, `[ FILMES ]`, `[ SÉRIES ]`, `[ BUSCA ]`), abas de temporadas e fontes de streaming:

```kotlin
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
```

### 6.9 Barra de Progresso / Seekbar Interativa para TV (`NeoSeekbar`)
Permite ao usuário arrastar o progresso do filme usando o controle remoto (D-Pad Esquerda/Direita) com indicador visual (thumb `■`) e salto direto com confirmação `OK`:

```kotlin
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
            .height(28.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            val current = scrubPositionMs ?: currentPositionMs
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
                        else -> false
                    }
                } else false
            }
    ) {
        val totalWidth = maxWidth

        // Track de base
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

        // Linha Preenchida (Progresso)
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(trackHeight.dp)
                .background(NeoColors.Yellow, RectangleShape)
        )

        // Thumb Neo-Brutalista no Foco
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
```

### 6.10 Avaliação em 5 Quadrados (`NeoRatingBar`)
Substitui estrelas convencionais por blocos sólidos de terminal:

```kotlin
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
```

### 6.11 Selos de Metadados (`NeoBadge`) e Faixa Lateral (`NeoMetadataStripe`)

```kotlin
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
fun NeoMetadataStripe(
    content: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    stripeColor: Color = NeoColors.Yellow
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
```

### 6.12 Barra Superior de Navegação (`TopNavigationBar`)
Header técnico com logotipo do app e botões de filtro rápido:

```kotlin
@Composable
fun TopNavigationBar(
    activeFilter: String,
    onFilterSelect: (String) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NeoDimens.SafeH, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "CINETV //",
            style = NeoType.Hero,
            color = NeoColors.Yellow
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoPillButton(
                text = "INÍCIO",
                isSelected = (activeFilter == "INICIO"),
                onClick = { onFilterSelect("INICIO") }
            )
            NeoPillButton(
                text = "FILMES",
                isSelected = (activeFilter == "FILMES"),
                onClick = { onFilterSelect("FILMES") }
            )
            NeoPillButton(
                text = "SÉRIES",
                isSelected = (activeFilter == "SERIES"),
                onClick = { onFilterSelect("SERIES") }
            )
            NeoPillButton(
                text = "BUSCA",
                onClick = onSearchClick
            )
        }
    }
}
```

### 6.13 Hero Banner com Correção de Rolagem D-Pad
Banner imersivo com backdrop w780, gradiente vertical seco e botões essenciais:

```kotlin
@Composable
fun HeroBanner(
    item: MediaItem,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onScrollToTop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(350.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.fullBackdropUrl)
                .crossfade(100)
                .build(),
            contentDescription = item.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            NeoColors.Bg.copy(alpha = 0.3f),
                            NeoColors.Bg.copy(alpha = 0.8f),
                            NeoColors.Bg
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = NeoDimens.SafeH, vertical = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeoBadge(text = item.year)
                NeoBadge(text = if (item.mediaType == "tv") "SÉRIE" else "FILME")
                NeoRatingBar(voteAverage = item.voteAverage)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.displayTitle,
                style = NeoType.Hero,
                color = NeoColors.TextPrimary,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.overview,
                style = NeoType.Body,
                color = NeoColors.TextSecondary,
                maxLines = 2,
                modifier = Modifier.width(620.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NeoButton(
                    text = "[ ASSISTIR AGORA ]",
                    onClick = onPlay,
                    variant = NeoButtonVariant.Primary,
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) onScrollToTop() }
                )
                NeoButton(
                    text = "[ DETALHES ]",
                    onClick = onDetails,
                    variant = NeoButtonVariant.Secondary,
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) onScrollToTop() }
                )
            }
        }
    }
}
```

### 6.14 Player HUD Compacto e Minimalista
Ocupa menos de 110dp no rodapé e deixa mais de 85% do vídeo totalmente descoberto:
- Linha 1: Título em caixa alta (`NOME // T1E3 - TÍTULO`) e tempo decorrido / duração com indicação de salto.
- Linha 2: `NeoSeekbar` interativa.
- Linha 3: Botões minimalistas de controle (`ArrowBack`, `Replay10`, `PlayArrow/Pause`, `Forward10`, `[ PRÓXIMO EP ]` e pílulas de seleção de fontes).

### 6.15 Campo de Busca Estilo Terminal (`NeoSearchField`)
Prompt técnico `> BUSCAR: [ ... ]` integrado com teclado virtual do Android TV:

```kotlin
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
```

### 6.16 Skeletons e Estados de Erro
- **Skeleton de Carregamento:** Retângulos planos com fade suave de opacidade (`0.7f -> 0.3f -> 0.7f` em loop de 800ms). Proibido usar shimmer com gradiente em movimento contínuo na CPU/GPU de TVs.
- **Estado Vazio:** Caixa delimitada por borda tracejada de 2dp com a cor `BorderRest`:
  `NENHUM TÍTULO ENCONTRADO PARA O TERMO ESPECIFICADO.`
- **Estado de Erro:** Mensagem técnica com código em `NeoColors.Red`:
  `FALHA NA CONEXÃO // CÓDIGO TMDB_TIMEOUT` seguido do botão `[ TENTAR DE NOVO ]`.

---

## 7. Ícones, TMDB e Imagens

1. **Biblioteca de Ícones:** Material Symbols ou Icons oficiais com traço fino e uniforme de 2dp. É terminantemente proibido o uso de emojis gráficos da web ou de smartphones.
2. **Resoluções Fixas do TMDB (Otimizadas para TV):**
   - Pôsteres para cards: `https://image.tmdb.org/t/p/w342/...`
   - Backdrops para Hero e telas de detalhes: `https://image.tmdb.org/t/p/w780/...`
   - Thumbnails de episódios: `https://image.tmdb.org/t/p/w300/...`
   - É proibido usar imagens em resolução `original` para prevenir Out-Of-Memory na Smart TV.
3. **Pipeline do Coil:**
   - `crossfade(100)` para transição suave de carregamento.
   - Cache de bitmap dimensionado estritamente para telas de 1GB a 2GB de RAM.

---

## 8. Movimento e Interações D-Pad

- **Ganho de Foco:** `150ms` de duração com curva `tween(150)` (`FastOutSlowInEasing`).
- **Clique Mecânico:** `90ms` com deslocamento de `2.dp` e retorno instantâneo.
- **Princípio de Movimento Único:** **Apenas o elemento em foco se move.** Nenhum carrossel vizinho, fundo ou título se anima de forma independente para garantir taxa sólida de 60 fps.
- **Restauração de Rolagem ao Topo:** Ao navegar com D-Pad Up a partir de qualquer carrossel para os botões do Hero Banner ou para a Barra Superior, o container `LazyColumn` deve obrigatoriamente executar `viewModel.listState.animateScrollToItem(0, 0)` para que o banner e o topo nunca sejam cortados.

---

## 9. Tom de Texto, Copywriting e Regras de Streaming

- **Idioma:** Português do Brasil (pt-BR).
- **Estilo:** Telegráfico, direto e técnico.
- **Rótulos Padrão de Seções:**
  - `CONTINUAR ASSISTINDO // EM ANDAMENTO`
  - `EM ALTA // SEMANA`
  - `FILMES POPULARES`
  - `SÉRIES POPULARES`
  - `MELHORES AVALIADOS`
- **Rótulos Padrão de Botões:**
  - `[ ASSISTIR AGORA ]` / `[ CONTINUAR // 14:30 ]`
  - `[ DETALHES ]`
  - `[ DO INÍCIO ]`
  - `[ PRÓXIMO EP ]`
  - `[ TENTAR OUTRA FONTE ]`
- **Crédito Obrigatório:** Na tela de sobre ou rodapé:
  `"Este produto usa a API TMDB, mas não é endossado ou certificado pelo TMDB."`

---

## 10. Adaptação para Televisão (TV-First & Performance)

1. **Navegação Exclusiva por D-Pad:** Toda ação requer um alvo focável via `focusRequester`, `onFocusChanged` e teclas `KEYCODE_DPAD_*`.
2. **Persistência Leve de Histórico:** O progresso é armazenado com `SharedPreferences` em formato JSON leve. Zero consumo de bateria ou locks de banco de dados pesados.
3. **Prevenção de Vazamento de Conexões:** Todas as chamadas de rede com `OkHttpClient` fecham explicitamente o `ResponseBody` via bloco `response.use { ... }` para evitar esgotamento do pool de sockets.
4. **Proteção contra Deadlock de GPU/MediaCodec:** Durante a reprodução de vídeo via hardware MediaCodec/SurfaceView, comandos de captura de tela de sistema (`screencap`) devem ser evitados para não travar o driver MMA em chipsets de TV modestos.

---

## 11. Tokens Oficiais para Kotlin

```kotlin
package com.pedro.cinetv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object NeoColors {
    val Bg = Color(0xFF0A0A1A)
    val Surface = Color(0xFF1A1A35)
    val SurfaceFocused = Color(0xFF24196B)
    val Navy = Color(0xFF170E4A)
    val Yellow = Color(0xFFD5E022)
    val YellowPressed = Color(0xFFC2CE1F)
    val Red = Color(0xFFED1A25)
    val Green = Color(0xFF22C55E)
    val Blue = Color(0xFF3B82F6)
    val Slate = Color(0xFF64748B)

    val TextPrimary = Color(0xFFF4F4F0)
    val TextSecondary = Color(0xB3F4F4F0) // 70%
    val TextMuted = Color(0x80F4F4F0)     // 50%
    val BorderRest = Color(0x59F4F4F0)    // 35%
    val BorderStrong = Color(0xFFF4F4F0)
}

object NeoDimens {
    val SafeH = 48.dp
    val SafeV = 27.dp

    val BorderRest = 2.dp
    val BorderFocus = 3.dp
    val StripeAccent = 4.dp
    val ShadowFocus = 6.dp

    val PosterW = 132.dp
    val PosterH = 198.dp                  // Proporção 2:3
    val BackdropW = 240.dp
    val BackdropH = 135.dp                // Proporção 16:9
    val ButtonH = 48.dp
    val Gap = 16.dp
    val Gap2 = 24.dp
}

object NeoMotion {
    const val FocusMs = 150
    const val PressMs = 90
    const val PosterScale = 1.08f
    const val ButtonScale = 1.04f
}

val JetBrainsMono = FontFamily.Monospace

object NeoType {
    val Hero = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    )
    val Section = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.5.sp
    )
    val CardTitle = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp
    )
    val Body = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
    val Button = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 1.sp
    )
    val Badge = TextStyle(
        fontFamily = JetBrainsMono,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.5.sp
    )
}
```

---

## 12. Tabela de Mapeamento: Web / Figma → Jetpack Compose for TV

| Elemento / Padrão | Componente Compose for TV | Especificação e Adaptação de Estilo |
| :-- | :-- | :-- |
| Card de Pôster | `NeoPosterCard` | Escala 1.08x, borda amarela 3dp, sombra dura 6dp via `drawBehind` |
| Card de Destaque | `NeoBackdropCard` | Proporção 16:9, gradiente escuro vertical na base, título 18sp |
| Card de Continuar | `NeoContinueWatchingCard` | Backdrop com barra de progresso amarela na base e badge `T{s}E{e}` |
| Card de Episódio | `NeoEpisodeCard` | Thumbnail 260x150dp, título do episódio e runtime |
| Botão Primário | `NeoButton(variant = Primary)` | Fundo amarelo no foco, texto Navy, borda 2dp em repouso |
| Botão Secundário | `NeoButton(variant = Secondary)` | Fundo Navy em repouso, foco invertido para amarelo |
| Botão de Ícone | `NeoIconButton` | Caixa 42dp, ícone vetorial de linha, zero emojis |
| Pílula / Filtro / Aba | `NeoPillButton` | Caixa 42dp, badge em caixa alta, seleção em amarelo |
| Barra de Progresso TV | `NeoSeekbar` | Track expansível de 4dp para 8dp, thumb visual `■`, saltos D-pad |
| Carrossel Horizontal | `LazyRow` | `contentPadding = PaddingValues(16.dp, 48.dp)` para acomodar sombra e escala |
| Lista Vertical | `LazyColumn` | Suporte a rolagem automática com `animateScrollToItem(0, 0)` ao subir para o topo |
| Banner Hero | `HeroBanner` | Imagem w780, gradiente vertical seco, sinopse restrita a 2 linhas |
| Barra de Topo | `TopNavigationBar` | Logotipo `CINETV //` em 40sp e pílulas de navegação rápida |
| HUD do Player | `PlayerScreen` HUD | Barra compacta <110dp, fundo com gradiente sutil, botões minimalistas |
| Campo de Busca | `NeoSearchField` | Prompt estilo terminal `> BUSCAR: [ ... ]` integrado com IME de TV |
| Selo de Nota | `NeoRatingBar` | 5 blocos retangulares `■■■■□` + valor numérico em 14sp |
| Ficha de Sinopse | `NeoMetadataStripe` | Barra lateral sólida de 4dp em amarelo ou vermelho |

---

## 13. Dez Regras de Ouro (Validação do Agente)

1. **Raios 100% Retos:** Todo e qualquer componente deve usar `RectangleShape` (raio zero).
2. **Tríade de Foco Obrigatória:** O foco de card exige: escala `1.08x` + borda `3.dp` `#D5E022` + sombra dura de `6.dp` `#D5E022` + `zIndex(1f)`.
3. **Zero Blur:** Proibição absoluta de desfoque gaussiano em sombras ou planos de fundo.
4. **Fonte Monoespaçada:** 100% dos textos utilizam estilo terminal monoespaçado, com tamanho mínimo de `14sp`.
5. **Contraste no Amarelo:** Texto sobre `#D5E022` é **sempre** `#170E4A` (Navy), nunca branco.
6. **Respeito à Safe Area:** Margens de segurança de `48.dp` (H) e `27.dp` (V) presentes em todas as telas.
7. **Tema Escuro Permanente:** Fundo exclusivamente `#0A0A1A`, sem modo claro.
8. **Controles em Caixa Alta:** Botões, chips, badges e títulos de fileiras sempre em UPPERCASE.
9. **D-Pad Determinístico & Auto-Scroll:** Subir com o controle remoto restaura o banner completo com `animateScrollToItem(0, 0)`.
10. **Zero Emojis:** Todos os controles usam ícones vetoriais de linha pura ou caracteres tipográficos ASCII.

---

## 14. Checklist de Fidelidade e Conformidade

- [x] Resolução lógica de 960x540 dp validada via ADB na Smart TV física (`192.168.1.53:5555`).
- [x] Paleta `NeoColors` e dimensões `NeoDimens` sem modificação de valores hexadecimais.
- [x] `contentPadding` de 16dp configurado em todos os `LazyRow` para não cortar sombras de foco de 6dp.
- [x] Nota do filme representada por 5 blocos quadrados `■■■■□`, nunca por estrelas convencionais.
- [x] Seletor de Temporadas e Episódios de Séries integrado à API do TMDB.
- [x] Reprodução nativa de vídeo via ExoPlayer + MegaEmbed com barra de progresso interativa (`NeoSeekbar`).
- [x] Histórico de reprodução persistido localmente e exibido na fileira `CONTINUAR ASSISTINDO`.
- [x] Rolagem automática ao topo restaurando o banner ao subir o foco.
- [x] Todos os emojis removidos em favor de ícones vetoriais técnicos minimalistas.
- [x] Navegação fluida a 60 fps contínuos no processador da TV real, sem travamentos ao segurar as setas do D-pad.
