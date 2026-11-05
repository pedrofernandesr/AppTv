# PLANO MESTRE DE AUDITORIA, OTIMIZAÇÃO E MELHORIAS // CINETV
**Documento Diretriz de Varredura e Evolução Arquitetural para Modelos de IA Avançados**  
*Data de Criação: Novembro de 2026 | Versão do Projeto: v1.0.0 (Produção Estável na TV)*

---

## 1. VISÃO GERAL & ARQUITETURA ATUAL DO PROJETO

O **CineTV** é um aplicativo nativo para **Android TV / Google TV (API 26+)**, desenvolvido 100% em **Kotlin** e **Jetpack Compose para TV**. O objetivo do aplicativo é proporcionar uma experiência de streaming de alta velocidade, com foco e navegação ergonômica em controle remoto (D-Pad), catálogo atualizado via **TMDB API v3** e reprodução via **MegaEmbed** com player embutido de baixa latência (**ExoPlayer / Media3**).

### 1.1 Stack Tecnológico
- **Linguagem:** Kotlin 1.9.22 (JVM 17)
- **UI Toolkit:** Jetpack Compose + Compose Foundation + Material 3 (adaptado para TV)
- **Design System:** Neo-Brutalismo Técnico Estrito (especificado em `ESTILO.md`)
- **Reprodução de Vídeo:** AndroidX Media3 (ExoPlayer 1.2.1) + Headless WebView Fallback
- **Rede & APIs:** Retrofit 2.9.0 + OkHttp 4.12.0 + Gson 2.10.1
- **Carregamento de Imagens:** Coil Compose 2.5.0
- **Persistência Local:** `PlaybackHistoryManager` (SharedPreferences + JSON serializado)
- **Dispositivo Alvo Principal:** Smart TV TCL 4K / Android TV 11+ (Controle Remoto `TCL_RC902V_M2`, resolução 1080p/4K).

### 1.2 Mapa Estrutural do Repositório
```
app/src/main/java/com/pedro/cinetv/
├── CineTvApp.kt                     # Application Class + Inicialização de Imagens (Coil)
├── MainActivity.kt                  # Single Activity + Roteamento de Telas (Screen State Machine)
├── data/
│   ├── api/
│   │   ├── TmdbApi.kt               # Interface Retrofit com endpoints da TMDB
│   │   ├── TmdbClient.kt            # OkHttpClient configurado com chave de API
│   │   ├── TmdbRepository.kt        # Repositório de metadados, trending, busca e temporadas
│   │   └── MegaEmbedRepository.kt   # Extrator direto HTTP (OkHttp) de fontes MP4/M3U8
│   ├── local/
│   │   └── PlaybackHistoryManager.kt# Gerenciador de histórico, progresso ms e continuar assistindo
│   └── model/
│       ├── TmdbModels.kt            # TmdbItem, TvDetailsResponse, TvEpisode, etc.
│       └── StreamSource.kt          # Fontes de vídeo extraídas (label, file, qualidade)
└── ui/
    ├── components/
    │   └── NeoComponents.kt         # NeoCard, NeoPosterCard, NeoButton, NeoBadge, NeoPillButton, etc.
    ├── screens/
    │   ├── home/
    │   │   ├── HomeScreen.kt        # Hero banner dinâmico + Carrosséis de catálogo + Continuar Assistindo
    │   │   └── HomeViewModel.kt     # ViewModel com UiState reativo e rolagem de catálogo
    │   ├── details/
    │   │   └── DetailsScreen.kt     # Sinopse, temporadas/episódios, progresso e remoção do histórico
    │   ├── player/
    │   │   └── PlayerScreen.kt      # Player ExoPlayer com OSD neo-brutalista, timeline e seleção de fontes
    │   └── search/
    │       └── SearchScreen.kt      # Teclado e grid adaptativo de busca TMDB
    └── theme/
        ├── Color.kt                 # Paleta: Yellow #D5E022, Navy #0B0F19, Surface #121826
        ├── Dimens.kt                # Escalas, paddings, safe areas de TV (52dp) e elevações
        ├── Theme.kt                 # Provedor do tema
        └── Type.kt                  # Tipografia mono/bold neo-brutalista
```

---

## 2. REQUISITOS DE DESIGN SYSTEM (ESTREITAMENTE OBRIGATÓRIO)

Qualquer alteração ou novo componente adicionado deve seguir à risca o arquivo `ESTILO.md`:
1. **Zero Emojis:** Proibido uso de emojis (como 🎬, ⭐, 🔍, 🍿). Substituir por badges textuais (`[ SÉRIE ]`, `[ FILME ]`), ícones vetoriais Material (`Icons.Default.*`) ou notação tipográfica (`//`, `>`, `<`).
2. **Formas Retangulares Rígidas:** Usar sempre `shape = RectangleShape` (zero `RoundedCornerShape`).
3. **Foco e Sombras:** O preenchimento amarelo `#D5E022` é **exclusivo** do elemento atualmente focado pelo controle remoto. Em repouso, usar fundo escuro com bordas e texto contrastantes. Sombra rígida projetada (`drawBehind` com `Offset(4.dp, 4.dp)`).
4. **Margens de TV Seguras (Overscan Safe Areas):** Manter no mínimo `52.dp` de respiro horizontal nas bordas da TV (`NeoDimens.SafeH`).

---

## 3. CHECKLIST DE VARREDURA E AUDITORIA (ROTEIRO PARA A IA)

Ao assumir o projeto, o modelo deve executar uma auditoria metódica dividida nas 5 fases a seguir:

### FASE 1: Varredura de Erros Estáticos, Memory Leaks e Ciclo de Vida
- [ ] **Auditoria de Ciclo de Vida do ExoPlayer (`PlayerScreen.kt`):**
  - Verificar se instâncias de `ExoPlayer` e `headlessWebView` são 100% destruídas no `onDispose`.
  - Garantir que a `headlessWebView` limpe cache, remova callbacks javascript e invoque `.destroy()` determinístico ao sair da tela, evitando retenção de memória em TVs com 1.5GB a 2GB de RAM.
- [ ] **Escopos de Coroutines:**
  - Auditar todos os usos de `rememberCoroutineScope()` nas telas. Garantir que jobs não fiquem pendentes ou orfãos durante navegação rápida entre telas.
  - Verificar se loops de atualização periódica (ex: loop de 1s de progresso no `PlayerScreen.kt`) são cancelados imediatamente quando o player é fechado.
- [ ] **Null Safety & Robustez em Modelos JSON (`TmdbModels.kt`):**
  - Auditar propriedades nulas de APIs externas: TMDB e MegaEmbed podem retornar campos ausentes, nulos ou arrays vazios em certos títulos raros.
  - Garantir que `voteAverage`, `overview`, `posterPath`, `backdropPath` e `firstAirDate` nunca causem `NullPointerException` ou formatações erradas como `"null"`.

### FASE 2: Otimização de Performance no Compose (Recomposições & TV D-Pad)
- [ ] **Estabilidade de Classes para Recomposição:**
  - Anotar classes de modelo (`TmdbItem`, `StreamSource`, `ContinueWatchingItem`, `TvEpisode`) com `@Immutable` ou `@Stable` para que o Compose Compiler pule recomposições desnecessárias durante a navegação pelo controle.
- [ ] **LazyRow e Rolagem Antecipada:**
  - Auditar o cálculo `(index - 2).coerceAtLeast(0)` em todos os carrosséis (`CatalogRow`, `ContinueWatching`, `seasons_selector`, `episodes_row`).
  - Assegurar que ao navegar rapidamente para a direita ou esquerda com o D-Pad pressionado continuamente, o `animateScrollToItem` não engasgue a UI thread nem cause stuttering em 60Hz.
- [ ] **Gerenciamento de Imagens (Coil):**
  - Verificar as configurações de cache em memória e em disco (`ImageLoader` em `CineTvApp.kt`).
  - Otimizar o redimensionamento dos backdrops da TMDB (`w780` vs `w1280`) e posters (`w342`), garantindo uso eficiente da VRAM.

### FASE 3: Otimizações no ExoPlayer e Motor de Streaming
- [ ] **Cache em Disco de Vídeo (SimpleCache Media3):**
  - Implementar cache em disco LRU (ex: 200MB a 500MB) para fragmentos HLS/MP4 com `CacheDataSource.Factory` e `SimpleCache`, permitindo que pequenos retrocessos na timeline sejam instantâneos sem novo download.
- [ ] **Seleção de Faixas de Áudio e Legendas:**
  - Adicionar no menu do Player (`PlayerScreen.kt`) um seletor visual neo-brutalista de faixas de áudio (`player.trackSelector`): Português (Dublado), Original (Inglês, etc.) e Legendas embutidas (`TrackGroup`).
- [ ] **Resiliência e Fallback de Extração (`MegaEmbedRepository.kt`):**
  - Adicionar suporte a múltiplos domínios espelho de fallback caso `mgeb.lat` ou `mgeb.space` sofram bloqueios temporários de DNS ou instabilidade.
  - Otimizar a detecção de streams HLS (`.m3u8`) com bitrate adaptativo para conexões lentas de Wi-Fi.

### FASE 4: Modernização da Persistência Local
- [ ] **Avaliação SharedPreferences vs Jetpack DataStore / Room:**
  - Atualmente, `PlaybackHistoryManager.kt` utiliza `SharedPreferences` com serialização JSON via Gson para a lista de continuar assistindo e timestamps de episódios.
  - Avaliar a migração para **Jetpack DataStore (Preferences)** ou **Room Database**:
    - Criar tabela `playback_history`: `id`, `title`, `posterPath`, `backdropPath`, `mediaType`, `season`, `episode`, `episodeTitle`, `positionMs`, `durationMs`, `updatedAt`.
    - Transações assíncronas via `Flow` reativo para atualização em tempo real da HomeScreen sem necessidade de releitura manual.

### FASE 5: Enriquecimento de Recursos & Funcionalidades da TV
- [ ] **Filtro de Gêneros TMDB:**
  - Expandir a barra superior (`INÍCIO`, `FILMES`, `SÉRIES`) ou criar uma fileira dedicada de tags/gêneros: *Ação, Comédia, Ficção Científica, Suspense, Terror, Animação*.
- [ ] **Busca Aprimorada (`SearchScreen.kt`):**
  - Adicionar histórico recente de buscas salvas localmente.
  - Adicionar sugestões rápidas ou busca por voz do controle remoto (`SpeechRecognizer` / tecla Mic do `RC902V`).
- [ ] **Seekbar com Saltos de 10s e Prévia:**
  - Aprimorar o arraste rápido (D-pad Left/Right no Player) com saltos inteligentes de 10 segundos e indicador visual do ponto de destino antes do `seekTo`.

---

## 4. GUIA DE COMANDOS DE BUILD, TESTE E DEPLOY NA TV

Para validar as alterações diretamente no hardware da TV ou no emulador local:

```powershell
# 1. Compilação do APK de Debug
./gradlew.bat assembleDebug

# 2. Copiar para a raiz do repositório
Copy-Item app/build/outputs/apk/debug/app-debug.apk -Destination CineTV-v1.0.0.apk -Force

# 3. Instalação direta na Smart TV (TCL via IP local na porta 5555)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 192.168.1.53:5555 install -r CineTV-v1.0.0.apk

# 4. Reinicialização do aplicativo na TV
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 192.168.1.53:5555 shell am force-stop com.pedro.cinetv
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 192.168.1.53:5555 shell am start -n com.pedro.cinetv/.MainActivity

# 5. Captura de tela para validação visual
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 192.168.1.53:5555 shell screencap -p /sdcard/tv_audit.png
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 192.168.1.53:5555 pull /sdcard/tv_audit.png ./tv_audit.png
```

---

## 5. HISTÓRICO RECENTE DE CORREÇÕES IMPLEMENTADAS (NÃO REGREDIR)

- **Banner Hero & TopBar:** O banner agora fica fora da `LazyColumn`, sincronizando perfeitamente altura (390dp expandido / 80dp recolhido) sem cortar o título da primeira seção.
- **Identificação de Séries (Sherlock):** Removido default `"movie"` em `TmdbModels.kt`. Adicionada detecção resiliente `isTv` via `firstAirDate`, `name` e consulta de temporadas.
- **Exclusão de Continuar Assistindo:** Botão `[ EXCLUIR DO CONTINUAR ASSISTINDO ]` implementado dentro de `DetailsScreen.kt`, limpando o histórico e atualizando a UI reativamente.
- **Rolagem com Foco Seguro (Penúltimo/Antepenúltimo):** Implementada rolagem antecipada nos carrosséis com `(index - 2).coerceAtLeast(0)` e respiro final de 36dp, impedindo que o seletor amarelo neon atravesse ou encoste na borda direita da TV.
