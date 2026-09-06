package com.idumet.frank.productdata

enum class ProductActionKindV1(val wireName: String) {
    NONE("none"),
    SAVE_CURRENT_PLACE("save_current_place"),
    UPDATE_SAVED_PLACE("update_saved_place"),
    DELETE_SAVED_PLACE("delete_saved_place"),
    LIST_SAVED_PLACES("list_saved_places"),
    SAVE_CURRENT_HAZARD("save_current_hazard"),
    RESOLVE_HAZARD("resolve_hazard"),
    LIST_HAZARDS("list_hazards"),
    WHERE_AM_I("where_am_i"),
    WHATS_NEARBY("whats_nearby"),
    LOST_ASSISTANCE("lost_assistance")
}

data class ProductActionProposalV1(
    val schemaVersion: String = PRODUCT_DATA_VERSION,
    val action: String,
    val placeName: String? = null,
    val newPlaceName: String? = null,
    val hazardType: String? = null,
    val description: String? = null
)

object ProductActionValidatorV1 {
    private val placeNameRequired = setOf(
        ProductActionKindV1.UPDATE_SAVED_PLACE.wireName,
        ProductActionKindV1.DELETE_SAVED_PLACE.wireName
    )
    private val hazardTypes = setOf(
        "pothole", "uneven_surface", "obstruction", "roadworks", "other"
    )

    fun validate(proposal: ProductActionProposalV1) {
        require(proposal.schemaVersion == PRODUCT_DATA_VERSION) {
            "Unsupported product-action version"
        }
        require(ProductActionKindV1.entries.any { it.wireName == proposal.action }) {
            "Unknown product action"
        }
        if (proposal.action in placeNameRequired) {
            requireValidPlaceName(proposal.placeName)
        }
        proposal.placeName?.let(::requireValidPlaceName)
        proposal.newPlaceName?.let(::requireValidPlaceName)
        if (proposal.action == ProductActionKindV1.SAVE_CURRENT_HAZARD.wireName) {
            require(proposal.hazardType in hazardTypes) { "A valid hazard type is required" }
        }
        require((proposal.description?.length ?: 0) <= 300) { "Hazard description is too long" }
    }

    private fun requireValidPlaceName(value: String?) {
        require(!value.isNullOrBlank() && value.length <= 80) { "Place name is invalid" }
        require(value.none { it.isISOControl() }) { "Place name contains unsafe characters" }
    }
}
