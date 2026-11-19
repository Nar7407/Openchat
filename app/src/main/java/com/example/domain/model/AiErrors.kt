package com.example.domain.model

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class AiErrorCategory {
    INVALID_KEY,
    RATE_LIMIT,
    UNSUPPORTED_MODEL,
    NETWORK_PROXY,
    TIMEOUT,
    CANCELLED,
    UNKNOWN
}

class AiException(
    val category: AiErrorCategory,
    override val message: String,
    val httpStatus: Int? = null,
    val suggestProxySetting: Boolean = false,
    val providerId: Long? = null,
    cause: Throwable? = null
) : Exception(message, cause)

object AiErrorMapper {

    fun fromHttpStatus(
        statusCode: Int,
        rawBody: String,
        providerName: String,
        providerId: Long? = null
    ): AiException {
        val extracted = extractApiMessage(rawBody)
        return when (statusCode) {
            401, 403 -> AiException(
                category = AiErrorCategory.INVALID_KEY,
                message = "Authentication failed ($statusCode) for $providerName. Check that your API key is valid and active.${detailSuffix(extracted)}",
                httpStatus = statusCode,
                providerId = providerId
            )
            404 -> AiException(
                category = AiErrorCategory.UNSUPPORTED_MODEL,
                message = "Endpoint or model not found (404) on $providerName. Verify the Model ID and Base URL.${detailSuffix(extracted)}",
                httpStatus = statusCode,
                suggestProxySetting = true,
                providerId = providerId
            )
            400, 422 -> AiException(
                category = AiErrorCategory.UNSUPPORTED_MODEL,
                message = "Request rejected ($statusCode) by $providerName. This model may not support the selected capability or input.${detailSuffix(extracted)}",
                httpStatus = statusCode,
                providerId = providerId
            )
            429 -> AiException(
                category = AiErrorCategory.RATE_LIMIT,
                message = "Rate limit or quota exceeded (429) for $providerName. Wait a moment or check your billing quota.${detailSuffix(extracted)}",
                httpStatus = statusCode,
                providerId = providerId
            )
            in 500..599 -> AiException(
                category = AiErrorCategory.NETWORK_PROXY,
                message = "Provider server error ($statusCode) from $providerName. Try again shortly or verify your Proxy / Gateway URL.${detailSuffix(extracted)}",
                httpStatus = statusCode,
                suggestProxySetting = true,
                providerId = providerId
            )
            else -> AiException(
                category = AiErrorCategory.UNKNOWN,
                message = "Unexpected HTTP $statusCode from $providerName.${detailSuffix(extracted)}",
                httpStatus = statusCode,
                providerId = providerId
            )
        }
    }

    fun fromThrowable(
        throwable: Throwable,
        providerName: String,
        providerId: Long? = null
    ): AiException {
        if (throwable is AiException) return throwable
        return when (throwable) {
            is SocketTimeoutException -> AiException(
                category = AiErrorCategory.TIMEOUT,
                message = "Request to $providerName timed out. The model or network may be congested.",
                suggestProxySetting = true,
                providerId = providerId,
                cause = throwable
            )
            is UnknownHostException, is ConnectException, is SSLException -> AiException(
                category = AiErrorCategory.NETWORK_PROXY,
                message = "Could not connect to $providerName (${throwable.javaClass.simpleName}). Check your internet connection, local server port, or configure a Proxy / Gateway URL in Settings.",
                suggestProxySetting = true,
                providerId = providerId,
                cause = throwable
            )
            is IOException -> {
                if (throwable.message?.contains("Canceled", ignoreCase = true) == true) {
                    AiException(
                        category = AiErrorCategory.CANCELLED,
                        message = "Generation stopped.",
                        providerId = providerId,
                        cause = throwable
                    )
                } else {
                    AiException(
                        category = AiErrorCategory.NETWORK_PROXY,
                        message = "Network error communicating with $providerName: ${throwable.message ?: "connection interrupted"}. If blocked by a firewall or web-origin policy, set a Proxy URL in Settings.",
                        suggestProxySetting = true,
                        providerId = providerId,
                        cause = throwable
                    )
                }
            }
            else -> AiException(
                category = AiErrorCategory.UNKNOWN,
                message = throwable.message ?: "Unexpected error communicating with $providerName.",
                providerId = providerId,
                cause = throwable
            )
        }
    }

    private fun extractApiMessage(rawBody: String): String {
        val trimmed = rawBody.trim()
        if (trimmed.isEmpty()) return ""
        val messageRegex = Regex(""""message"\s*:\s*"([^"]+)"""")
        val match = messageRegex.find(trimmed)
        if (match != null) {
            return match.groupValues[1].take(180)
        }
        return trimmed.take(160).replace("\n", " ")
    }

    private fun detailSuffix(detail: String): String {
        return if (detail.isNotBlank()) "\nDetails: $detail" else ""
    }
}
