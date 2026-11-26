package jp.usuiensan.taxijokemeter.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrsReverseGeocoderTest {

    @Test
    fun parsesPeliasReverseGeocodeResponse() {
        val body = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {"type": "Point", "coordinates": [135.7585, 34.9855]},
                  "properties": {
                    "id": "12345",
                    "gid": "openstreetmap:address:12345",
                    "layer": "address",
                    "source": "openstreetmap",
                    "name": "東塩小路町 676",
                    "housenumber": "676",
                    "street": "烏丸通",
                    "postalcode": "600-8216",
                    "locality": "京都市",
                    "localadmin": "下京区",
                    "region": "京都府",
                    "country": "日本",
                    "label": "東塩小路町 676, 京都市, 京都府, 日本"
                  }
                }
              ]
            }
        """.trimIndent()
        val result = parseOrsReverseGeocode(body)
        assertEquals("京都市下京区", result?.municipality)
        assertEquals("烏丸通", result?.roadName)
        assertEquals("676", result?.houseNumber)
        assertEquals("京都市下京区676", result?.reportAddress)
        assertEquals("京都市下京区", result?.areaText)
        assertEquals("京都府京都市下京区676", result?.canonicalAddress)
    }

    @Test
    fun parsesNeighbourhoodIntoTown() {
        val body = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {"type": "Point", "coordinates": [135.7585, 34.9855]},
                  "properties": {
                    "layer": "street",
                    "name": "烏丸通",
                    "locality": "京都市",
                    "localadmin": "下京区",
                    "neighbourhood": "東塩小路町",
                    "region": "京都府",
                    "label": "烏丸通, 京都市, 京都府, 日本"
                  }
                }
              ]
            }
        """.trimIndent()
        val result = parseOrsReverseGeocode(body)
        assertEquals("東塩小路町", result?.town)
        // layer=street の name が通り名になる
        assertEquals("烏丸通", result?.roadName)
        assertEquals("京都市下京区東塩小路町", result?.reportAddress)
    }

    @Test
    fun returnsNullForEmptyFeatures() {
        assertNull(parseOrsReverseGeocode("""{"type":"FeatureCollection","features":[]}"""))
    }
}
