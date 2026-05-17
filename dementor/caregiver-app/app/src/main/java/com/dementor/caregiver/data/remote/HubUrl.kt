package com.dementor.caregiver.data.remote

import java.net.URI

/**
 * Normalizes user input into a base URL for the phone hub (FastAPI / uvicorn).
 * - Adds http:// if missing
 * - For http URLs with no explicit port, defaults to :8000 (uvicorn default)
 * - For https URLs with no explicit port, keeps default (443)
 */
fun normalizeHubBaseUrl(input: String): String {
    val trimmed = input.trim().trimEnd('/')
    if (trimmed.isEmpty()) throw IllegalArgumentException("Hub address is empty")

    val withScheme =
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "http://$trimmed"
        }

    val uri = URI(withScheme)
    val scheme = uri.scheme ?: throw IllegalArgumentException("Invalid URL")
    val host = uri.host ?: throw IllegalArgumentException("Invalid host")

    if (uri.port != -1) {
        return withScheme.trimEnd('/')
    }

    val path = uri.path.orEmpty()
    val query = uri.query?.let { "?$it" } ?: ""

    return if (scheme.equals("https", ignoreCase = true)) {
        "https://$host$path$query".trimEnd('/')
    } else {
        "http://$host:8000$path$query".trimEnd('/')
    }
}
