package com.sarvaai.app.core.result

sealed class AppError(open val message: String, open val cause: Throwable? = null) {
    data class NetworkError(override val message: String = "No network connection or timeout", override val cause: Throwable? = null) : AppError(message, cause)
    data class AuthExpired(override val message: String = "Authentication token expired", override val cause: Throwable? = null) : AppError(message, cause)
    data class ServiceUnavailable(override val message: String = "Upstream service temporarily unavailable", override val cause: Throwable? = null) : AppError(message, cause)
    data class EmptyTranscript(override val message: String = "No speech detected", override val cause: Throwable? = null) : AppError(message, cause)
    data class UnsupportedLanguage(val languageCode: String, override val message: String = "Unsupported language: $languageCode") : AppError(message)
    data class RateLimited(override val message: String = "API rate limit exceeded", override val cause: Throwable? = null) : AppError(message, cause)
    data class OcrExtractionFailed(override val message: String = "Failed to extract menu structure from image", override val cause: Throwable? = null) : AppError(message, cause)
    data class ValidationError(override val message: String) : AppError(message)
}
