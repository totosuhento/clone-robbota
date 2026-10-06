package com.robotta.ai

/** Hasil "Buat Konten AI" untuk satu produk. */
data class AiContent(
    val titles: List<String>,
    val description: String,
    val hashtags: String,
    val categories: List<String>
)
