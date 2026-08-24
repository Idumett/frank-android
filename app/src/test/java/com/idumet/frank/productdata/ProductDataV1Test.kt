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
}
