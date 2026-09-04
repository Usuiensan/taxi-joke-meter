package jp.usuiensan.taxijokemeter.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceCandidatePolicyTest {
    @Test fun nearbyPoiIsAccepted() {
        val candidate = chooseFacilityCandidate(35.0, 135.0, 10.0, listOf(PlaceCandidate("公園", 35.00009, 135.0, "park", listOf("park"))))
        assertEquals("公園", candidate?.name)
    }

    @Test fun distantPoiIsRejected() {
        val candidate = chooseFacilityCandidate(35.0, 135.0, 10.0, listOf(PlaceCandidate("コンビニ", 35.00072, 135.0, "convenience_store", listOf("convenience_store"))))
        assertNull(candidate)
    }

    @Test fun administrativeCandidateIsRejected() {
        val candidate = chooseFacilityCandidate(35.0, 135.0, 10.0, listOf(PlaceCandidate("宇治市", 35.00009, 135.0, "locality", listOf("locality"))))
        assertNull(candidate)
    }
}
