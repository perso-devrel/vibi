package com.dubcast.shared.domain.util

val HEX_COLOR_PATTERN = Regex("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})$")

fun isValidHexColor(value: String): Boolean = HEX_COLOR_PATTERN.matches(value)
