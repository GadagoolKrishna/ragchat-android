package com.ragchat.api.llm

import com.ragchat.api.model.Locality

/**
 * Feature profile and capacity limits of an LLM provider.
 *
 * @property contextWindow Maximum input token capacity.
 * @property maxOutputTokens Maximum output tokens generated in a single response.
 * @property streaming Whether streaming response tokens is supported.
 * @property toolCalling Whether tool or function calling is natively supported.
 * @property multimodal Whether images or audio input modalities are accepted.
 * @property locality Execution locality of the model (LOCAL or CLOUD).
 * @property dataResidency Data sovereignty / storage region identifier if cloud-hosted.
 */
public data class LlmCapabilities(
    val contextWindow: Int,
    val maxOutputTokens: Int,
    val streaming: Boolean,
    val toolCalling: Boolean,
    val multimodal: Boolean,
    val locality: Locality,
    val dataResidency: String? = null,
)
