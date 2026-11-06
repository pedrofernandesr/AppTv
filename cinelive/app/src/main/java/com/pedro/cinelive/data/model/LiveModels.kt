package com.pedro.cinelive.data.model

import com.google.gson.annotations.SerializedName

data class CategoriesResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: List<CategoryItem> = emptyList(),
    @SerializedName("total") val total: Int = 0
)

data class CategoryItem(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String
)

data class ChannelsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: List<ChannelItem> = emptyList(),
    @SerializedName("total") val total: Int = 0
)

data class ChannelItem(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("logo_url") val logoUrl: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("embeds") val embeds: List<ChannelEmbed> = emptyList(),
    @SerializedName("epg") val epg: ChannelEpg? = null
)

data class ChannelEmbed(
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("quality") val quality: String? = null,
    @SerializedName("embed_url") val embedUrl: String? = null
)

data class ChannelEpg(
    @SerializedName("current") val current: EpgProgram? = null,
    @SerializedName("next") val next: EpgProgram? = null
)

data class EpgProgram(
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("formatted_time") val formattedTime: String? = null,
    @SerializedName("start_time") val startTime: Long? = null,
    @SerializedName("end_time") val endTime: Long? = null,
    @SerializedName("image") val image: String? = null
)

data class SportsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: List<SportsEvent> = emptyList(),
    @SerializedName("total") val total: Int = 0
)

data class SportsEvent(
    @SerializedName("id") val id: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("competition") val competition: String? = null,
    @SerializedName("start_time") val startTime: String? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("embeds") val embeds: List<ChannelEmbed> = emptyList()
)

enum class StreamType {
    DIRECT_M3U8,
    WEB_EMBED
}

data class ResolvedStream(
    val type: StreamType,
    val url: String,
    val headers: Map<String, String> = emptyMap()
)
