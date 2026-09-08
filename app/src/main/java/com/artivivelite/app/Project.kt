package com.artivivelite.app

data class Project(
    val id: String,
    val name: String,
    val targetPath: String,
    val targetWidthMeters: Float,
    val contentPath: String,
    val contentType: String
)
