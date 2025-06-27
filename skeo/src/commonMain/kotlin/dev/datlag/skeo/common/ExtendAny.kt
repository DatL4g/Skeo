package dev.datlag.skeo.common

internal fun String.swapCase(): String {
    return this.map { char ->
        when {
            char.isUpperCase() -> char.lowercaseChar()
            char.isLowerCase() -> char.uppercaseChar()
            else -> char // Keep non-alphabetic characters as they are
        }
    }.joinToString(separator = "")
}