package jp.usuiensan.taxijokemeter.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenRouteServiceReverseGeocoderTest {
    @Test fun venueNameWithoutStreetIsFacilityOnly() {
        val result = OpenRouteServiceReverseGeocoder(apiKey = "x").parse("""{"features":[{"properties":{"layer":"venue","name":"正道第4公園"}}]}""")!!

        assertEquals("正道第4公園", result.facilityName)
        assertEquals(null, result.roadName)
    }

    @Test fun venueUsesStreetAsRoadAndNameAsFacility() {
        val result = OpenRouteServiceReverseGeocoder(apiKey = "x").parse("""{"features":[{"properties":{"layer":"venue","name":"道の駅○○","street":"国道1号"}}]}""")!!

        assertEquals("道の駅○○", result.facilityName)
        assertEquals("国道1号", result.roadName)
    }

    @Test fun addressUsesStreetAsRoadButNotNameAsFacility() {
        val result = OpenRouteServiceReverseGeocoder(apiKey = "x").parse("""{"features":[{"properties":{"layer":"address","name":"1-1 内堀通り","street":"内堀通り"}}]}""")!!

        assertEquals("内堀通り", result.roadName)
        assertEquals(null, result.facilityName)
    }

    @Test fun streetLayerCanUseNameAsRoad() {
        val result = OpenRouteServiceReverseGeocoder(apiKey = "x").parse("""{"features":[{"properties":{"layer":"street","name":"国道1号"}}]}""")!!

        assertEquals("国道1号", result.roadName)
        assertEquals(null, result.facilityName)
    }

    @Test fun localityNameIsNeitherRoadNorFacility() {
        val result = OpenRouteServiceReverseGeocoder(apiKey = "x").parse("""{"features":[{"properties":{"layer":"locality","name":"宇治市"}}]}""")!!

        assertEquals(null, result.facilityName)
        assertEquals(null, result.roadName)
    }

    @Test fun facilityNameIsNotUsedAsRoadOrAddressFallback() {
        val result = OpenRouteServiceReverseGeocoder(apiKey = "x").parse("""{"features":[{"properties":{"layer":"venue","label":"Uji-shi Uji-shi 正道第4公園","municipality":"Uji-shi","locality":"宇治市","district":"○○","name":"正道第4公園"}}]}""")!!

        assertEquals(null, result.roadName)
        assertEquals("正道第4公園", result.facilityName)
        assertEquals("宇治市○○", result.reportAddress)
        assertEquals("Uji-shi Uji-shi 正道第4公園", result.canonicalAddress)
    }

    @Test fun peliasPropertiesAreNormalizedForMeterAndPersistence() {
        val result = OpenRouteServiceReverseGeocoder(apiKey = "x").parse("""{"features":[{"properties":{"layer":"venue","label":"東京都千代田区千代田1-1","municipality":"千代田区","locality":"千代田","district":"千代田","street":"内堀通り","housenumber":"1-1","postalcode":"100-0001","name":"皇居"}}]}""")!!
        assertEquals("内堀通り", result.roadName)
        assertEquals("1-1", result.houseNumber)
        assertEquals("東京都千代田区千代田1-1", result.canonicalAddress)
        assertEquals("皇居", result.facilityName)
        assertTrue(result.reportAddress!!.contains("千代田区"))
    }
}
