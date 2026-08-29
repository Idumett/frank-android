package com.idumet.frank.productdata

import org.junit.Assert.assertThrows
import org.junit.Test

class ProductDataV1Test {
    private val entity = MemoryEntityV1(
        id = "memory-fixture-001", ownerId = "dan",
        createdAt = "2026-08-24T08:15:00.000Z", updatedAt = "2026-08-24T08:15:00.000Z",
        provenance = ProductProvenanceV1("chivora_fixture", "request-fixture-001"),
        memoryType = "object", title = "Front door key", content = "The key is in the blue bowl."
    )

    @Test fun acceptsTheV1FixtureShape() {
        ProductDataValidatorV1.validate(PutProductDataRequestV1(
            requestId = "request-fixture-001", ownerId = "dan",
            idempotencyKey = "write-fixture-001", entity = entity
        ))
    }

    @Test fun rejectsMismatchedOwnership() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductDataValidatorV1.validate(PutProductDataRequestV1(
                requestId = "request-fixture-001", ownerId = "another-user",
                idempotencyKey = "write-fixture-001", entity = entity
            ))
        }
    }

    @Test fun validatesConfirmedPlacesAndExpiringHazards() {
        val provenance = ProductProvenanceV1("frank_server", "request-location-001")
        val location = SavedLocationV1(51.5, -0.1, 12.0)
        val confirmation = UserConfirmationV1(confirmedAt = "2026-08-28T10:00:00.000Z")
        ProductDataValidatorV1.validate(KnownPlaceEntityV1(
            id = "place-gym", ownerId = "dan", createdAt = "2026-08-28T10:00:00.000Z",
            updatedAt = "2026-08-28T10:00:00.000Z", provenance = provenance,
            name = "gym", category = "gym", location = location, confirmation = confirmation
        ))
        ProductDataValidatorV1.validate(HazardEntityV1(
            id = "hazard-pothole", ownerId = "dan", createdAt = "2026-08-28T10:00:00.000Z",
            updatedAt = "2026-08-28T10:00:00.000Z", provenance = provenance,
            hazardType = "pothole", description = "pothole", location = location,
            radiusMeters = 12.0, reportedAt = "2026-08-28T10:00:00.000Z",
            expiresAt = "2026-09-27T10:00:00.000Z", confirmation = confirmation
        ))
    }

    @Test fun rejectsHazardsWithoutAValidExpiry() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductDataValidatorV1.validate(HazardEntityV1(
                id = "hazard-bad", ownerId = "dan", createdAt = "2026-08-28T10:00:00.000Z",
                updatedAt = "2026-08-28T10:00:00.000Z",
                provenance = ProductProvenanceV1("frank_server", "request-location-001"),
                hazardType = "pothole", description = "pothole",
                location = SavedLocationV1(51.5, -0.1, 12.0), radiusMeters = 12.0,
                reportedAt = "2026-08-28T10:00:00.000Z", expiresAt = "2026-08-27T10:00:00.000Z",
                confirmation = UserConfirmationV1(confirmedAt = "2026-08-28T10:00:00.000Z")
            ))
        }
    }
}
