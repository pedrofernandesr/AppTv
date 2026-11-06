package com.pedro.cinelive.data.epg

import com.pedro.cinelive.data.model.ChannelItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ActiveEpgInfo(
    val title: String,
    val formattedTime: String,
    val nextTitle: String? = null,
    val nextFormattedTime: String? = null
)

object DynamicEpgProvider {

    data class ProgramSlot(
        val startMinutes: Int,
        val endMinutes: Int,
        val title: String,
        val startTimeStr: String,
        val endTimeStr: String
    )

    private fun slot(startH: Int, startM: Int, endH: Int, endM: Int, title: String): ProgramSlot {
        val startMin = startH * 60 + startM
        val endMin = if (endH == 24) 24 * 60 else endH * 60 + endM
        val startStr = String.format(Locale.US, "%02d:%02d", startH, startM)
        val endStr = if (endH == 24) "00:00" else String.format(Locale.US, "%02d:%02d", endH, endM)
        return ProgramSlot(startMin, endMin, title, startStr, endStr)
    }

    private val GLOBO_WEEKDAY = listOf(
        slot(0, 20, 1, 10, "Conversa com Bial"),
        slot(1, 10, 4, 0, "Comédia na Madruga"),
        slot(4, 0, 6, 0, "Hora Um"),
        slot(6, 0, 8, 30, "Bom Dia Praça / Bom Dia Brasil"),
        slot(8, 30, 10, 35, "Encontro com Patrícia Poeta"),
        slot(10, 35, 11, 45, "Mais Você"),
        slot(11, 45, 13, 0, "Praça TV 1ª Edição"),
        slot(13, 0, 13, 25, "Globo Esporte"),
        slot(13, 25, 14, 45, "Jornal Hoje"),
        slot(14, 45, 15, 30, "Edição Especial: Novela"),
        slot(15, 30, 17, 5, "Sessão da Tarde"),
        slot(17, 5, 18, 5, "Vale a Pena Ver de Novo"),
        slot(18, 5, 19, 15, "Garota do Momento"),
        slot(19, 15, 19, 45, "Praça TV 2ª Edição"),
        slot(19, 45, 20, 30, "Volta por Cima"),
        slot(20, 30, 21, 20, "Jornal Nacional"),
        slot(21, 20, 22, 25, "Mania de Você"),
        slot(22, 25, 23, 30, "Globo Repórter / Cinema"),
        slot(23, 30, 24, 0, "Jornal da Globo")
    )

    private val SBT_WEEKDAY = listOf(
        slot(0, 0, 1, 0, "Programa do Ratinho"),
        slot(1, 0, 2, 0, "The Noite com Danilo Gentili"),
        slot(2, 0, 2, 45, "Operação Mesquita"),
        slot(2, 45, 6, 0, "SBT News na Madrugada"),
        slot(6, 0, 9, 30, "Primeiro Impacto"),
        slot(9, 30, 11, 15, "Chega Mais"),
        slot(11, 15, 13, 30, "Chega Mais Notícias"),
        slot(13, 30, 14, 30, "Carinha de Anjo"),
        slot(14, 30, 15, 30, "Quando me Apaixono"),
        slot(15, 30, 16, 30, "Fofocalizando"),
        slot(16, 30, 19, 45, "Tá na Hora"),
        slot(19, 45, 20, 45, "SBT Brasil"),
        slot(20, 45, 21, 30, "A Caverna Encantada"),
        slot(21, 30, 22, 15, "As Aventuras de Poliana"),
        slot(22, 15, 23, 15, "Programa do Ratinho"),
        slot(23, 15, 24, 0, "A Praça É Nossa")
    )

    private val BAND_WEEKDAY = listOf(
        slot(0, 0, 1, 0, "Jornal da Noite"),
        slot(1, 0, 6, 0, "Esporte Total / Vídeos Incríveis"),
        slot(6, 0, 8, 0, "Bora Brasil"),
        slot(8, 0, 9, 25, "Bora SP"),
        slot(9, 25, 11, 0, "The Chef / Dia a Dia"),
        slot(11, 0, 13, 0, "Jogo Aberto"),
        slot(13, 0, 14, 30, "Os Donos da Bola"),
        slot(14, 30, 16, 0, "Melhor da Tarde"),
        slot(16, 0, 19, 20, "Brasil Urgente"),
        slot(19, 20, 20, 30, "Jornal da Band"),
        slot(20, 30, 21, 20, "Melhor da Noite"),
        slot(21, 20, 22, 20, "Show da Fé"),
        slot(22, 20, 24, 0, "Linha de Shows / MasterChef")
    )

    private val RECORD_WEEKDAY = listOf(
        slot(0, 0, 5, 0, "Programação IURD"),
        slot(5, 0, 7, 0, "Balanço Geral Manhã"),
        slot(7, 0, 8, 40, "Balanço Geral SP"),
        slot(8, 40, 10, 0, "Fala Brasil"),
        slot(10, 0, 11, 50, "Hoje em Dia"),
        slot(11, 50, 15, 30, "Balanço Geral SP"),
        slot(15, 30, 16, 30, "Novela da Tarde"),
        slot(16, 30, 19, 55, "Cidade Alerta"),
        slot(19, 55, 21, 0, "Jornal da Record"),
        slot(21, 0, 22, 30, "Novela / Série Bíblica"),
        slot(22, 30, 24, 0, "Linha de Shows / Reality")
    )

    private val CNN_WEEKDAY = listOf(
        slot(0, 0, 6, 0, "CNN Madrugada"),
        slot(6, 0, 9, 0, "CNN Novo Dia"),
        slot(9, 0, 12, 0, "Live CNN Brasil"),
        slot(12, 0, 15, 0, "Bastidores CNN"),
        slot(15, 0, 18, 0, "O Grande Debate / CNN 360"),
        slot(18, 0, 20, 0, "CNN Arena"),
        slot(20, 0, 22, 0, "CNN Prime Time"),
        slot(22, 0, 24, 0, "Jornal da CNN")
    )

    private val BANDSPORTS_WEEKDAY = listOf(
        slot(0, 0, 6, 0, "BandSports Notícias"),
        slot(6, 0, 9, 0, "Primeiro Tempo"),
        slot(9, 0, 12, 0, "BandSports Especial / Noticiário"),
        slot(12, 0, 14, 0, "BandSports Online"),
        slot(14, 0, 16, 0, "G4 BandSports"),
        slot(16, 0, 18, 0, "Baita Amigos / Pré-Jogo"),
        slot(18, 0, 20, 0, "Super Motor / Automobilismo"),
        slot(20, 0, 22, 0, "Depois do Jogo"),
        slot(22, 0, 24, 0, "BandSports Notícias")
    )

    private val CARTOON_WEEKDAY = listOf(
        slot(0, 0, 6, 0, "Madrugada Cartoon: Animações Clássicas"),
        slot(6, 0, 10, 0, "Manhã Cartoon: Jovens Titãs em Ação"),
        slot(10, 0, 12, 0, "O Incrível Mundo de Gumball"),
        slot(12, 0, 14, 0, "Hora de Aventura / Ursos Sem Curso"),
        slot(14, 0, 16, 0, "Steven Universo / Craig do Riacho"),
        slot(16, 0, 18, 0, "Jovens Titãs em Ação / Clarêncio"),
        slot(18, 0, 20, 0, "Especial Desenhos Cartoon"),
        slot(20, 0, 22, 0, "Dragon Ball Super / Anime Bloco"),
        slot(22, 0, 24, 0, "Adult Swim / Bloco Jovem")
    )

    private val DISCOVERY_KIDS_WEEKDAY = listOf(
        slot(0, 0, 6, 0, "Bons Sonhos Discovery Kids"),
        slot(6, 0, 9, 0, "Peppa Pig e Amigos"),
        slot(9, 0, 11, 0, "O Show da Luna / Mini Beat Power Rockers"),
        slot(11, 0, 13, 0, "Patrulha Canina"),
        slot(13, 0, 15, 0, "As Aventuras de Paddington"),
        slot(15, 0, 17, 0, "Ricky Zoom e Amigos"),
        slot(17, 0, 19, 0, "Peppa Pig / 44 Gatos"),
        slot(19, 0, 21, 0, "Clube do Filme Infantil"),
        slot(21, 0, 24, 0, "Histórias da Noite Kids")
    )

    private val DISCOVERY_CHANNEL_WEEKDAY = listOf(
        slot(0, 0, 6, 0, "Documentários da Madrugada"),
        slot(6, 0, 9, 0, "Mestres da Engenharia"),
        slot(9, 0, 12, 0, "Planeta Selvagem / Natureza Extrema"),
        slot(12, 0, 15, 0, "Caçadores de Mitos / Grandes Obras"),
        slot(15, 0, 18, 0, "Febre do Ouro / Sobrevivência"),
        slot(18, 0, 21, 0, "Largados e Pelados"),
        slot(21, 0, 24, 0, "Expedição Sobrevivência")
    )

    private val ANIMAL_PLANET_WEEKDAY = listOf(
        slot(0, 0, 6, 0, "Mundo Selvagem da Madrugada"),
        slot(6, 0, 9, 0, "Reino das Feras"),
        slot(9, 0, 12, 0, "Operação Tanzânia / Selva Viva"),
        slot(12, 0, 15, 0, "Gatos Selvagens / Predadores"),
        slot(15, 0, 18, 0, "Patrulha Animal / Resgate"),
        slot(18, 0, 21, 0, "Monstros do Rio"),
        slot(21, 0, 24, 0, "Planeta Animal Especial")
    )

    private val GLOBONEWS_WEEKDAY = listOf(
        slot(0, 0, 6, 0, "GloboNews Madrugada"),
        slot(6, 0, 9, 0, "GloboNews Em Ponto"),
        slot(9, 0, 13, 0, "Conexão GloboNews"),
        slot(13, 0, 16, 0, "Estúdio i"),
        slot(16, 0, 18, 0, "GloboNews Mais"),
        slot(18, 0, 20, 0, "Edição das 18h"),
        slot(20, 0, 22, 0, "GloboNews Em Pauta"),
        slot(22, 0, 23, 30, "Jornal das Dez"),
        slot(23, 30, 24, 0, "Especial de Notícias")
    )

    private val REDETV_WEEKDAY = listOf(
        slot(0, 0, 6, 0, "Programação da Madrugada"),
        slot(6, 0, 8, 45, "Manhã Notícias"),
        slot(8, 45, 11, 40, "Você na TV"),
        slot(11, 40, 13, 0, "Qualé, Moré?"),
        slot(13, 0, 15, 0, "Igreja Universal"),
        slot(15, 0, 17, 0, "A Tarde É Sua com Sonia Abrão"),
        slot(17, 0, 18, 0, "Hora de Ação"),
        slot(18, 0, 19, 30, "RedeTV! News"),
        slot(19, 30, 20, 30, "TV Fama"),
        slot(20, 30, 21, 30, "Show da Fé"),
        slot(21, 30, 23, 0, "Superpop / Linha de Shows"),
        slot(23, 0, 24, 0, "Leitura Dinâmica")
    )

    private val CULTURA_WEEKDAY = listOf(
        slot(0, 0, 6, 0, "Cultura na Madrugada"),
        slot(6, 0, 11, 0, "Quintal da Cultura"),
        slot(11, 0, 12, 0, "Metrópolis / Repórter Eco"),
        slot(12, 0, 13, 0, "Jornal da Tarde"),
        slot(13, 0, 17, 0, "Programas Educativos e Infantis"),
        slot(17, 0, 18, 30, "Cocoricó / Shaun o Carneiro"),
        slot(18, 30, 19, 30, "Jornal da Cultura"),
        slot(19, 30, 21, 0, "Roda Viva / Persona"),
        slot(21, 0, 24, 0, "Documentários Cultura")
    )

    fun getActiveEpg(channel: ChannelItem): ActiveEpgInfo {
        val now = Calendar.getInstance()
        val nowSeconds = System.currentTimeMillis() / 1000

        // 1. Verifica se a API forneceu um EPG com timestamps válidos e não expirados
        val current = channel.epg?.current
        if (current != null && current.endTime != null && current.endTime > nowSeconds) {
            val startTime = current.startTime ?: nowSeconds
            if (startTime <= nowSeconds + 600) {
                val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
                val startStr = timeFormat.format(Date(startTime * 1000))
                val endStr = timeFormat.format(Date(current.endTime * 1000))
                val formatted = "Hoje, das $startStr às $endStr"

                val next = channel.epg.next
                val nextTitle = next?.title
                val nextFormatted = if (next?.startTime != null && next.endTime != null) {
                    val nStart = timeFormat.format(Date(next.startTime * 1000))
                    val nEnd = timeFormat.format(Date(next.endTime * 1000))
                    "das $nStart às $nEnd"
                } else null

                return ActiveEpgInfo(
                    title = current.title ?: "Programação Ao Vivo",
                    formattedTime = formatted,
                    nextTitle = nextTitle,
                    nextFormattedTime = nextFormatted
                )
            }
        }

        // 2. Se o EPG da API está vencido/expirado, resolve pela Grade Dinâmica em Tempo Real
        val channelIdLower = channel.id.lowercase()
        val grid = findGridForChannel(channelIdLower)

        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val dayFormat = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR"))
        val todayStr = dayFormat.format(now.time).replaceFirstChar { it.uppercase() }

        if (grid != null && grid.isNotEmpty()) {
            val slotIndex = grid.indexOfFirst { currentMinutes >= it.startMinutes && currentMinutes < it.endMinutes }
            if (slotIndex >= 0) {
                val currentSlot = grid[slotIndex]
                val nextSlot = if (slotIndex + 1 < grid.size) grid[slotIndex + 1] else grid.first()

                return ActiveEpgInfo(
                    title = currentSlot.title,
                    formattedTime = "$todayStr, das ${currentSlot.startTimeStr} às ${currentSlot.endTimeStr}",
                    nextTitle = nextSlot.title,
                    nextFormattedTime = "às ${nextSlot.startTimeStr}"
                )
            }
        }

        // 3. Fallback dinâmico com relógio local exato (nunca mostra horários ou dias falsos do passado)
        val timeFormat = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
        val timeStr = timeFormat.format(now.time)
        val cat = channel.category?.uppercase() ?: "GERAL"

        return ActiveEpgInfo(
            title = "Transmissão Ao Vivo // $cat",
            formattedTime = "$todayStr // $timeStr",
            nextTitle = "Programação Contínua",
            nextFormattedTime = "Ao Vivo"
        )
    }

    private fun findGridForChannel(channelId: String): List<ProgramSlot>? {
        return when {
            channelId.startsWith("globo") -> GLOBO_WEEKDAY
            channelId.startsWith("sbt") -> SBT_WEEKDAY
            channelId.startsWith("bandnews") -> GLOBO_WEEKDAY // ou noticiário
            channelId.startsWith("bandsports") -> BANDSPORTS_WEEKDAY
            channelId.startsWith("band") -> BAND_WEEKDAY
            channelId.startsWith("record") -> RECORD_WEEKDAY
            channelId.startsWith("cnn") -> CNN_WEEKDAY
            channelId.startsWith("cartoon") -> CARTOON_WEEKDAY
            channelId.startsWith("discoverykids") -> DISCOVERY_KIDS_WEEKDAY
            channelId.startsWith("discovery") -> DISCOVERY_CHANNEL_WEEKDAY
            channelId.startsWith("animal") -> ANIMAL_PLANET_WEEKDAY
            channelId.startsWith("globonews") -> GLOBONEWS_WEEKDAY
            channelId.startsWith("redetv") -> REDETV_WEEKDAY
            channelId.startsWith("cultura") || channelId.startsWith("tvcultura") -> CULTURA_WEEKDAY
            else -> null
        }
    }
}
