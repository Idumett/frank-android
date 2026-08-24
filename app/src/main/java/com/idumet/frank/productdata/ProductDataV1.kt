package com.idumet.frank.productdata

import java.time.Instant

const val PRODUCT_DATA_VERSION = "1"

enum class ProductEntityTypeV1(val wireName: String) {
    USER_PROFILE("user_profile"), SESSION("session"), MEMORY("memory"),
    CONVERSATION_TURN("conversation_turn"), PREFERENCES("preferences")
}

enum class ProductDataErrorCodeV1(val wireName: String) {
    INVALID_REQUEST("invalid_request"), UNAUTHORISED("unauthorised"), NOT_FOUND("not_found"),
    IDEMPOTENCY_CONFLICT("idempotency_conflict"), STORAGE_UNAVAILABLE("storage_unavailable")
}

data class ProductProvenanceV1(
    val source: String,
    val requestId: String,
    val provider: String? = null,
    val capturedAt: String? = null
)

sealed interface ProductEntityV1 {
    val schemaVersion: String
    val entityType: String
    val id: String
    val ownerId: String
    val createdAt: String
    val updatedAt: String
    val provenance: ProductProvenanceV1
}

data class UserProfileEntityV1(
    override val schemaVersion: String = PRODUCT_DATA_VERSION,
    override val entityType: String = ProductEntityTypeV1.USER_PROFILE.wireName,
    override val id: String,
    override val ownerId: String,
    override val createdAt: String,
    override val updatedAt: String,
    override val provenance: ProductProvenanceV1,
    val displayName: String? = null,
    val locale: String? = null
) : ProductEntityV1

data class SessionEntityV1(
    override val schemaVersion: String = PRODUCT_DATA_VERSION,
    override val entityType: String = ProductEntityTypeV1.SESSION.wireName,
    override val id: String,
    override val ownerId: String,
    override val createdAt: String,
    override val updatedAt: String,
    override val provenance: ProductProvenanceV1,
    val deviceId: String? = null,
    val lastSeenAt: String
) : ProductEntityV1

data class MemoryEntityV1(
    override val schemaVersion: String = PRODUCT_DATA_VERSION,
    override val entityType: String = ProductEntityTypeV1.MEMORY.wireName,
    override val id: String,
    override val ownerId: String,
    override val createdAt: String,
    override val updatedAt: String,
    override val provenance: ProductProvenanceV1,
    val memoryType: String,
    val title: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val mediaRef: String? = null,
    val embeddingRef: String? = null
) : ProductEntityV1

data class ConversationTurnEntityV1(
    override val schemaVersion: String = PRODUCT_DATA_VERSION,
    override val entityType: String = ProductEntityTypeV1.CONVERSATION_TURN.wireName,
    override val id: String,
    override val ownerId: String,
    override val createdAt: String,
    override val updatedAt: String,
    override val provenance: ProductProvenanceV1,
    val sessionId: String,
    val role: String,
    val text: String
) : ProductEntityV1

data class PreferencesEntityV1(
    override val schemaVersion: String = PRODUCT_DATA_VERSION,
    override val entityType: String = ProductEntityTypeV1.PREFERENCES.wireName,
    override val id: String,
    override val ownerId: String,
    override val createdAt: String,
    override val updatedAt: String,
    override val provenance: ProductProvenanceV1,
    val sessionId: String,
    val tts: TtsPreferencesV1
) : ProductEntityV1

data class TtsPreferencesV1(
    val languageCode: String? = null,
    val voiceName: String? = null,
    val speakingRate: Double? = null,
    val pitch: Double? = null
)

data class PutProductDataRequestV1(
    val schemaVersion: String = PRODUCT_DATA_VERSION,
    val requestId: String,
    val ownerId: String,
    val operation: String = "put",
    val idempotencyKey: String,
    val entity: ProductEntityV1
)

data class GetProductDataRequestV1(
    val schemaVersion: String = PRODUCT_DATA_VERSION,
    val requestId: String,
    val ownerId: String,
    val operation: String = "get",
    val entityType: String,
    val entityId: String
)

data class ListProductDataRequestV1(
    val schemaVersion: String = PRODUCT_DATA_VERSION,
    val requestId: String,
    val ownerId: String,
    val operation: String = "list",
    val entityType: String,
    val sessionId: String? = null,
    val limit: Int = 20
)

data class DeleteProductDataRequestV1(
    val schemaVersion: String = PRODUCT_DATA_VERSION,
    val requestId: String,
    val ownerId: String,
    val operation: String = "delete",
    val idempotencyKey: String,
    val entityType: String,
    val entityId: String
)

data class ProductDataErrorV1(val code: String, val message: String, val retryable: Boolean)
data class ProductDataResponseV1(
    val schemaVersion: String,
    val requestId: String,
    val ok: Boolean,
    val result: ProductDataResultV1? = null,
    val error: ProductDataErrorV1? = null,
    val idempotentReplay: Boolean = false
)
data class ProductDataResultV1(
    val kind: String,
    val entity: Map<String, Any?>? = null,
    val entities: List<Map<String, Any?>>? = null,
    val deleted: Boolean? = null
)

object ProductDataValidatorV1 {
    fun validate(request: PutProductDataRequestV1) {
        require(request.schemaVersion == PRODUCT_DATA_VERSION) { "Unsupported product-data version" }
        require(request.requestId.length >= 8) { "requestId is too short" }
        require(request.ownerId.isNotBlank()) { "ownerId is required" }
        require(request.idempotencyKey.length >= 8) { "idempotencyKey is too short" }
        require(request.entity.ownerId == request.ownerId) { "Entity owner does not match request owner" }
        validate(request.entity)
    }

    fun validate(entity: ProductEntityV1) {
        require(entity.schemaVersion == PRODUCT_DATA_VERSION) { "Unsupported entity version" }
        require(entity.id.isNotBlank() && entity.ownerId.isNotBlank()) { "Entity identity is required" }
        requireIso(entity.createdAt); requireIso(entity.updatedAt)
        require(entity.provenance.requestId.length >= 8) { "Provenance requestId is too short" }
        when (entity) {
            is UserProfileEntityV1 -> Unit
            is SessionEntityV1 -> requireIso(entity.lastSeenAt)
            is MemoryEntityV1 -> {
                require(entity.memoryType in setOf("text", "object", "scene", "mixed")) { "Unknown memory type" }
                require(entity.title.isNotBlank() && entity.content.isNotBlank()) { "Memory content is required" }
            }
            is ConversationTurnEntityV1 -> {
                require(entity.role == "user" || entity.role == "assistant") { "Unknown conversation role" }
                require(entity.sessionId.isNotBlank() && entity.text.isNotBlank()) { "Conversation data is required" }
            }
            is PreferencesEntityV1 -> require(entity.sessionId.isNotBlank()) { "sessionId is required" }
        }
    }

    fun validate(response: ProductDataResponseV1) {
        require(response.schemaVersion == PRODUCT_DATA_VERSION) { "Unsupported response version" }
        require(response.requestId.length >= 8) { "requestId is too short" }
        require((response.ok && response.result != null && response.error == null) ||
                (!response.ok && response.result == null && response.error != null)) { "Invalid response state" }
        response.error?.let { require(ProductDataErrorCodeV1.entries.any { code -> code.wireName == it.code }) { "Unknown product-data error" } }
    }

    private fun requireIso(value: String) { require(runCatching { Instant.parse(value) }.isSuccess) { "Invalid timestamp" } }
}
