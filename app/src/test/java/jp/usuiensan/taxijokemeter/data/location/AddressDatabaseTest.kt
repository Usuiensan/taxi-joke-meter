package jp.usuiensan.taxijokemeter.data.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressDatabaseTest {

    private val database: AddressDatabase by lazy {
        val bytes = javaClass.classLoader!!.getResourceAsStream("address_db_fixture.bin")!!.readBytes()
        AddressDatabase.load(bytes)!!
    }

    @Test
    fun lookupFindsJyukyoBlockPoint() {
        val hit = database.lookup(35.0005, 135.0005)
        assertNotNull(hit)
        hit!!
        assertTrue(hit.blockLevel)
        assertEquals(true, hit.jyukyo)
        assertEquals("テスト県", hit.prefecture)
        assertEquals("テスト市", hit.municipality)
        assertEquals("中央一丁目", hit.townRaw)
        assertEquals("8", hit.blockRaw)
        assertNull(hit.aza)
        assertTrue(hit.distanceMeters < 5.0)
    }

    @Test
    fun lookupFindsChibanBlockPointWithRawNotation() {
        val hit = database.lookup(34.9900, 134.9900)
        assertNotNull(hit)
        hit!!
        assertTrue(hit.blockLevel)
        assertEquals(false, hit.jyukyo)
        assertEquals("大字高田", hit.townRaw)
        assertEquals("前田", hit.aza)
        // 枝番のハイフンを含む原表記が保持される
        assertEquals("123-4", hit.blockRaw)
    }

    @Test
    fun lookupFallsBackToTownPoint() {
        val hit = database.lookup(35.0100, 135.0100)
        assertNotNull(hit)
        hit!!
        assertFalse(hit.blockLevel)
        assertNull(hit.jyukyo)
        assertEquals("山田", hit.townRaw)
        assertNull(hit.blockRaw)
    }

    @Test
    fun lookupReturnsNullFarAway() {
        assertNull(database.lookup(30.0, 130.0))
    }

    @Test
    fun nearestRoadNameFindsNamedRoadWithin40m() {
        assertEquals("テスト通り", database.nearestRoadName(35.0005, 135.0005))
    }

    @Test
    fun nearestRoadNameReturnsNullWhenFar() {
        assertNull(database.nearestRoadName(35.0100, 135.0100))
    }

    @Test
    fun buildOfflineAddressComposesJyukyoBan() {
        val hit = database.lookup(35.0005, 135.0005)!!
        val address = buildOfflineAddress(hit, "テスト通り")
        assertEquals("テスト市中央1丁目8番", address.reportAddress)
        assertEquals("テスト県テスト市中央1丁目8番", address.canonicalAddress)
        assertEquals("テスト市中央1丁目", address.areaText)
        assertEquals("テスト市", address.municipality)
        assertEquals("中央", address.town)
        assertEquals("1丁目", address.chome)
        assertEquals("8", address.block)
        assertEquals("テスト通り", address.roadName)
    }

    @Test
    fun buildOfflineAddressComposesChibanBanchi() {
        val hit = database.lookup(34.9900, 134.9900)!!
        val address = buildOfflineAddress(hit, null)
        // 大字＋小字＋地番（原表記）＋番地
        assertEquals("テスト市大字高田前田123-4番地", address.reportAddress)
        assertEquals("大字高田", address.town)
        assertNull(address.chome)
        assertEquals("前田", address.aza)
        assertEquals("123-4", address.block)
    }

    @Test
    fun splitTownChomeNormalizesKanjiNumerals() {
        assertEquals("毛馬町" to "3丁目", splitTownChome("毛馬町三丁目"))
        assertEquals("中央" to "12丁目", splitTownChome("中央十二丁目"))
        assertEquals("西池袋" to "1丁目", splitTownChome("西池袋1丁目"))
        assertEquals("山田" to null, splitTownChome("山田"))
        assertEquals("大字高田" to null, splitTownChome("大字高田"))
    }
}
