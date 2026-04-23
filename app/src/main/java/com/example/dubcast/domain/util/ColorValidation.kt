package com.example.dubcast.domain.util

/**
 * Shared regex for `#RRGGBB` or `#AARRGGBB` hex color strings used across
 * domain use cases (frame background, text overlay color, etc.).
 */
val HEX_COLOR_PATTERN = Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$")

fun isValidHexColor(value: String): Boolean = HEX_COLOR_PATTERN.matches(value)
