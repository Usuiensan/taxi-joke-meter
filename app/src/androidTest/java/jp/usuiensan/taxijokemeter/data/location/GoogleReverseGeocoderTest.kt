package jp.usuiensan.taxijokemeter.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleReverseGeocoderTest {
    @Test fun formattedAddressKeepsJapaneseTownOrder() {
        val result = parseGoogleReverseGeocode("""{"results":[{"formattedAddress":"京都府宇治市木幡熊小路","addressComponents":[{"longText":"宇治市","types":["locality"]},{"longText":"熊小路","types":["sublocality_level_1"]},{"longText":"木幡","types":["sublocality_level_2"]}]}]}""")!!
        assertEquals("宇治市木幡熊小路", result.reportAddress)
    }

    @Test fun routeComponentBecomesRoadName() {
        val result = parseGoogleReverseGeocode("""{"results":[{"formattedAddress":"京都府宇治市○○町1-1","types":["street_address"],"addressComponents":[{"longText":"国道24号","types":["route"]},{"longText":"宇治市","types":["locality"]},{"longText":"○○町","types":["sublocality_level_1"]},{"longText":"1-1","types":["street_number"]}]}]}""")!!
        assertEquals("国道24号", result.roadName)
        assertEquals("宇治市○○町1-1", result.reportAddress)
        assertEquals("京都府宇治市○○町1-1", result.canonicalAddress)
    }

    @Test fun poiOnlyDoesNotBecomeRoadName() {
        val result = parseGoogleReverseGeocode("""{"results":[{"formattedAddress":"正道第4公園, 宇治市","types":["park"],"addressComponents":[{"longText":"正道第4公園","types":["point_of_interest"]}]}]}""")!!
        assertNull(result.roadName)
        assertNull(result.facilityName)
        assertNull(result.reportAddress)
    }

    @Test fun englishFormattedAddressIsCanonicalOnly() {
        val result = parseGoogleReverseGeocode("""{"results":[{"formattedAddress":"Uji, Kyoto, Japan","types":["locality"],"addressComponents":[{"longText":"Uji","types":["locality"],"languageCode":"en"}]}]}""")!!
        assertEquals("Uji, Kyoto, Japan", result.canonicalAddress)
        assertNull(result.reportAddress)
    }

    @Test fun componentOrderDoesNotMatter() {
        val result = parseGoogleReverseGeocode("""{"results":[{"formattedAddress":"京都府宇治市○○町","addressComponents":[{"longText":"○○町","types":["sublocality"]},{"longText":"宇治市","types":["locality"]},{"longText":"京都府","types":["administrative_area_level_1"]}]}]}""")!!
        assertEquals("宇治市○○町", result.reportAddress)
    }
}
