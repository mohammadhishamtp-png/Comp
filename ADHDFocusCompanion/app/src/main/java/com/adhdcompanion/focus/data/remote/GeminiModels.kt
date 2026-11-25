package com.adhdcompanion.focus.data.remote

/**
 * Minimal request/response shapes for the Gemini generateContent REST
 * endpoint (https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent).
 * Only the fields this app actually uses are modeled.
 */

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null,
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    val role: String? = null,
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null
)

data class GeminiGenerationConfig(
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 1024,
    val responseMimeType: String? = null
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?
)

/** The text Gemini wants spoken back; used for the TTS endpoint response parsing. */
data class GeminiTtsResponse(
    val candidates: List<GeminiTtsCandidate>?
)

data class GeminiTtsCandidate(
    val content: GeminiTtsContent?
)

data class GeminiTtsContent(
    val parts: List<GeminiTtsPart>?
)

data class GeminiTtsPart(
    val inlineData: GeminiInlineData?
)

data class GeminiInlineData(
    val mimeType: String?,
    val data: String? // base64-encoded audio
)
