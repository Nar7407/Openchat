package com.example.data.remote

import com.example.domain.model.AiErrorMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedReader
import java.io.IOException

sealed interface SseLine {
    data class Data(val payload: String) : SseLine
    data class RawJson(val jsonBody: String) : SseLine
}

object SseClient {

    fun stream(
        client: OkHttpClient,
        request: Request,
        providerName: String,
        providerId: Long? = null
    ): Flow<SseLine> = callbackFlow {
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    close(AiErrorMapper.fromThrowable(e, providerName, providerId))
                } else {
                    close()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        val errBody = runCatching { res.body?.string().orEmpty() }.getOrDefault("")
                        close(AiErrorMapper.fromHttpStatus(res.code, errBody, providerName, providerId))
                        return
                    }
                    val body = res.body
                    if (body == null) {
                        close()
                        return
                    }
                    val contentType = res.header("Content-Type").orEmpty()
                    if (contentType.contains("application/json", ignoreCase = true) &&
                        !contentType.contains("text/event-stream", ignoreCase = true)
                    ) {
                        val raw = body.string()
                        trySend(SseLine.RawJson(raw))
                        close()
                        return
                    }

                    try {
                        val reader = BufferedReader(body.charStream())
                        val eventBuffer = StringBuilder()
                        while (true) {
                            if (call.isCanceled()) break
                            val line = reader.readLine() ?: break
                            when {
                                line.startsWith("data:") -> {
                                    val dataPart = line.removePrefix("data:").trimStart()
                                    if (dataPart == "[DONE]") {
                                        break
                                    }
                                    if (eventBuffer.isNotEmpty()) eventBuffer.append('\n')
                                    eventBuffer.append(dataPart)
                                }
                                line.isBlank() -> {
                                    if (eventBuffer.isNotEmpty()) {
                                        trySend(SseLine.Data(eventBuffer.toString()))
                                        eventBuffer.clear()
                                    }
                                }
                            }
                        }
                        if (eventBuffer.isNotEmpty()) {
                            trySend(SseLine.Data(eventBuffer.toString()))
                        }
                        close()
                    } catch (e: IOException) {
                        if (!call.isCanceled()) {
                            close(AiErrorMapper.fromThrowable(e, providerName, providerId))
                        } else {
                            close()
                        }
                    } catch (t: Throwable) {
                        close(AiErrorMapper.fromThrowable(t, providerName, providerId))
                    }
                }
            }
        })

        awaitClose {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)
}
