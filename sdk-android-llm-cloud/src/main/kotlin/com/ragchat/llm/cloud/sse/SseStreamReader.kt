package com.ragchat.llm.cloud.sse

import com.ragchat.api.error.SdkError
import com.ragchat.api.logging.LogLevel
import com.ragchat.api.logging.RagChatLogger
import com.ragchat.api.model.LlmEvent
import com.ragchat.llm.cloud.config.CloudClientConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Executes an SSE streaming request and streams parsed [LlmEvent] items via a cold [Flow].
 */
internal class SseStreamReader(
    private val config: CloudClientConfig,
    private val logger: RagChatLogger? = null,
) {
    private val client: OkHttpClient by lazy {
        val builder =
            OkHttpClient
                .Builder()
                .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(config.callTimeoutMs, TimeUnit.MILLISECONDS)

        config.certificatePinner?.let { builder.certificatePinner(it) }
        if (config.sslSocketFactory != null && config.trustManager != null) {
            builder.sslSocketFactory(config.sslSocketFactory, config.trustManager)
        }

        builder.build()
    }

    private val eventSourceFactory: EventSource.Factory by lazy {
        EventSources.createFactory(client)
    }

    /**
     * Streams events using the provided [request] and chunk parser.
     */
    fun stream(
        providerId: String,
        request: Request,
        parseChunk: (data: String) -> List<LlmEvent>,
    ): Flow<LlmEvent> =
        callbackFlow {
            val totalBytesReceived = AtomicLong(0L)

            val listener =
                object : EventSourceListener() {
                    override fun onOpen(
                        eventSource: EventSource,
                        response: Response,
                    ) {
                        logger?.log(LogLevel.DEBUG, "CloudLlm", "SSE connection opened for provider: $providerId (HTTP ${response.code})")
                    }

                    override fun onEvent(
                        eventSource: EventSource,
                        id: String?,
                        type: String?,
                        data: String,
                    ) {
                        val currentTotal = totalBytesReceived.addAndGet(data.length.toLong())
                        if (currentTotal > config.maxResponseSizeBytes) {
                            trySend(
                                LlmEvent.Error(
                                    SdkError.TokenLimitExceededError(
                                        maxAllowed = config.maxResponseSizeBytes.toInt(),
                                        actual = currentTotal.toInt(),
                                    ),
                                ),
                            )
                            eventSource.cancel()
                            channel.close()
                            return
                        }

                        if (data == "[DONE]") {
                            trySend(LlmEvent.Done)
                            return
                        }

                        try {
                            val events = parseChunk(data)
                            for (event in events) {
                                trySend(event)
                            }
                        } catch (e: org.json.JSONException) {
                            logger?.log(LogLevel.WARN, "CloudLlm", "Failed to parse SSE JSON chunk: ${e.message}")
                        } catch (e: IllegalArgumentException) {
                            logger?.log(LogLevel.WARN, "CloudLlm", "Malformed SSE event payload: ${e.message}")
                        }
                    }

                    override fun onClosed(eventSource: EventSource) {
                        logger?.log(LogLevel.DEBUG, "CloudLlm", "SSE connection closed for provider: $providerId")
                        trySend(LlmEvent.Done)
                        channel.close()
                    }

                    override fun onFailure(
                        eventSource: EventSource,
                        t: Throwable?,
                        response: Response?,
                    ) {
                        val code = response?.code
                        val sdkError =
                            when {
                                code == 401 || code == 403 -> {
                                    SdkError.AuthenticationError(providerId, "HTTP_$code")
                                }
                                code != null -> {
                                    SdkError.ModelUnavailableError(providerId, "HTTP_ERROR_$code", t)
                                }
                                else -> {
                                    SdkError.ModelUnavailableError(providerId, "NETWORK_FAILURE", t)
                                }
                            }
                        logger?.log(
                            LogLevel.ERROR,
                            "CloudLlm",
                            "SSE failure for $providerId: HTTP $code, error=${t?.javaClass?.simpleName}",
                        )
                        trySend(LlmEvent.Error(sdkError))
                        channel.close(t)
                    }
                }

            val eventSource = eventSourceFactory.newEventSource(request, listener)

            awaitClose {
                eventSource.cancel()
            }
        }
}
