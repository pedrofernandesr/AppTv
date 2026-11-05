package com.pedro.cinetv.data.model

import com.google.gson.annotations.SerializedName

data class StreamSource(
    @SerializedName("file") val file: String,
    @SerializedName("type") val type: String = "hls",
    @SerializedName("label") val label: String = "Opção"
)
