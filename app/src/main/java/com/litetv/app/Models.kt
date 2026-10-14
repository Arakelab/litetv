package com.litetv.app

data class Channel(
    val name: String,
    val url: String,
    val group: String,
    val logo: String = ""
)

data class Source(
    val name: String,
    val url: String
)
