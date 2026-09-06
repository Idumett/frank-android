package com.idumet.frank.productdata

import org.junit.Assert.assertThrows
import org.junit.Test

class ProductActionV1Test {
    @Test fun acceptsSaveCurrentPlaceWithOrWithoutAnInitialName() {
        ProductActionValidatorV1.validate(ProductActionProposalV1(
            action = "save_current_place",
            placeName = "Mum's house"
        ))
        ProductActionValidatorV1.validate(ProductActionProposalV1(
            action = "save_current_place"
        ))
    }

    @Test fun rejectsAnUpdateWithoutItsTargetPlace() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductActionValidatorV1.validate(ProductActionProposalV1(
                action = "update_saved_place"
            ))
        }
    }

    @Test fun rejectsUnknownActionsAndHazards() {
        assertThrows(IllegalArgumentException::class.java) {
            ProductActionValidatorV1.validate(ProductActionProposalV1(action = "run_sql"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProductActionValidatorV1.validate(ProductActionProposalV1(
                action = "save_current_hazard",
                hazardType = "dragon"
            ))
        }
    }
}
