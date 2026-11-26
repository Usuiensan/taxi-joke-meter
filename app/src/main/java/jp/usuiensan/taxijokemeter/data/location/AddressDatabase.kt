package jp.usuiensan.taxijokemeter.data.location

import jp.usuiensan.taxijokemeter.domain.location.ReverseGeocodeAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 同梱アセット address_db.bin（形式 v2）のリーダー（純 JVM 実装）。
 * バイナリ形式は tools/address-db/build_address_db.py のドキュメントを参照。
 *
 * 検索仕様:
 *  1. 街区レベルの最寄り点を 1000m 以内で探す
 *  2. なければ全レベルの最寄り点（最大 20000m）
 *  3. 通り名は指定半径（既定 40m）以内の最寄り名称つき道路セグメント
 */
class AddressDatabase private constructor(private val bytes: ByteArray) {

    data class AddressHit(
        val prefecture: String,
        val municipality: String,
        val townRaw: String,
        val aza: String?,
        val blockRaw: String?,
        /** 住居表示区域なら true、地番区域なら false。町丁目レベルでは null。 */
        val jyukyo: Boolean?,
        val blockLevel: Boolean,
        val distanceMeters: Double,
    )

    private data class DirEntry(val compOff: Int, val compSize: Int, val rawSize: Int)

    private data class RawPoint(
        val lat: Double,
        val lon: Double,
        val townIdx: Int,
        val flags: Int,
        val blockMain: Int,
        val blockSub: Int,
        val blockStrIdx: Int,
        val azaStrIdx: Int,
    )

    private class Cursor(val buf: ByteArray, var pos: Int) {
        fun varuint(): Int {
            var result = 0
            var shift = 0
            while (true) {
                val b = buf[pos++].toInt()
                result = result or ((b and 0x7F) shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }

        fun zigzag(): Int {
            val v = varuint()
            return (v ushr 1) xor -(v and 1)
        }

        fun u16(): Int {
            val v = (buf[pos].toInt() and 0xFF) or ((buf[pos + 1].toInt() and 0xFF) shl 8)
            pos += 2
            return v
        }

        fun u32(): Int {
            val v = (buf[pos].toInt() and 0xFF) or
                ((buf[pos + 1].toInt() and 0xFF) shl 8) or
                ((buf[pos + 2].toInt() and 0xFF) shl 16) or
                ((buf[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return v
        }

        fun skipVaruint() {
            while (buf[pos++].toInt() and 0x80 != 0) {
                // 継続ビットが落ちるまで進める
            }
        }
    }

    private class PointBlock(val cellOffsets: IntArray, val cellCounts: IntArray, val payload: ByteArray)
    private class RoadBlock(val cellOffsets: IntArray, val cellCounts: IntArray, val payload: ByteArray, val segTableOff: Int)

    private val stringCount: Int
    private val townCount: Int
    private val stringsOff: Int
    private val townsOff: Int
    private val prefOff: Int
    private val prefEntryCount: Int
    private val pointDir: Map<Long, DirEntry>
    private val roadDir: Map<Long, DirEntry>

    private val stringsData: ByteArray by lazy { inflateStrings() }
    private val strings: Array<String> by lazy { readStrings() }
    private val prefFirstTownIdx: IntArray by lazy { readPrefFirsts() }
    private val pointBlockCache = HashMap<Long, PointBlock>()
    private val roadBlockCache = HashMap<Long, RoadBlock>()

    init {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= HEADER_SIZE) { "address_db.bin が小さすぎます" }
        val magic = ByteArray(8)
        buf.get(magic)
        require(String(magic, Charsets.US_ASCII) == MAGIC) { "address_db.bin の magic が不正です" }
        require(buf.getInt(8) == FORMAT_VERSION) { "address_db.bin の形式バージョンが不正です" }
        stringCount = buf.getInt(12)
        townCount = buf.getInt(16)
        val pointBlockCount = buf.getInt(20)
        val roadBlockCount = buf.getInt(24)
        stringsOff = buf.getInt(28)
        townsOff = buf.getInt(32)
        val pointDirOff = buf.getInt(36)
        val roadDirOff = buf.getInt(40)
        prefOff = buf.getInt(52)
        prefEntryCount = (townsOff - prefOff) / 8
        pointDir = readDir(buf, pointDirOff, pointBlockCount)
        roadDir = readDir(buf, roadDirOff, roadBlockCount)
    }

    private fun readDir(buf: ByteBuffer, off: Int, count: Int): Map<Long, DirEntry> {
        val result = HashMap<Long, DirEntry>(count * 2)
        for (i in 0 until count) {
            val p = off + i * DIR_ENTRY_SIZE
            val blockLat = buf.getInt(p)
            val blockLon = buf.getInt(p + 4)
            result[key(blockLat, blockLon)] = DirEntry(buf.getInt(p + 8), buf.getInt(p + 12), buf.getInt(p + 16))
        }
        return result
    }

    private fun inflateStrings(): ByteArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val rawSize = buf.getInt(stringsOff)
        val compSize = prefOff - (stringsOff + 4)
        val inflater = Inflater()
        inflater.setInput(bytes, stringsOff + 4, compSize)
        val out = ByteArray(rawSize)
        var n = 0
        while (n < rawSize) {
            val k = inflater.inflate(out, n, rawSize - n)
            if (k <= 0) break
            n += k
        }
        inflater.end()
        check(n == rawSize) { "address_db.bin の文字列セクション展開に失敗しました" }
        return out
    }

    private fun readStrings(): Array<String> {
        val data = stringsData
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val offsets = IntArray(stringCount + 1)
        for (i in 0..stringCount) {
            offsets[i] = buf.getInt(i * 4)
        }
        val base = (stringCount + 1) * 4
        return Array(stringCount) { i ->
            String(data, base + offsets[i], offsets[i + 1] - offsets[i], Charsets.UTF_8)
        }
    }

    private fun readPrefFirsts(): IntArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(prefEntryCount) { i -> buf.getInt(prefOff + i * 8 + 4) }
    }

    private fun townStrings(townIdx: Int): Triple<String, String, String> {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val p = townsOff + townIdx * 8
        val s = strings
        val muni = s[buf.getInt(p)]
        val town = s[buf.getInt(p + 4)]
        // 都道府県は町の並び順（都道府県順）から二分探索で引く
        val firsts = prefFirstTownIdx
        var lo = 0
        var hi = prefEntryCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (firsts[mid] <= townIdx) lo = mid else hi = mid - 1
        }
        val pref = s[buf.getInt(prefOff + lo * 8)]
        return Triple(pref, muni, town)
    }

    private fun inflate(entry: DirEntry): ByteArray {
        val inflater = Inflater()
        inflater.setInput(bytes, entry.compOff, entry.compSize)
        val out = ByteArray(entry.rawSize)
        var n = 0
        while (n < entry.rawSize) {
            val k = inflater.inflate(out, n, entry.rawSize - n)
            if (k <= 0) break
            n += k
        }
        inflater.end()
        check(n == entry.rawSize) { "address_db.bin の展開に失敗しました" }
        return out
    }

    // ---------- 点ブロック ----------
    private fun pointBlock(blockLat: Int, blockLon: Int): PointBlock? {
        val k = key(blockLat, blockLon)
        pointBlockCache[k]?.let { return it }
        val entry = pointDir[k] ?: return null
        val payload = inflate(entry)
        val offsets = IntArray(CELLS_PER_BLOCK_TOTAL + 1)
        val counts = IntArray(CELLS_PER_BLOCK_TOTAL)
        val cursor = Cursor(payload, 0)
        for (cell in 0 until CELLS_PER_BLOCK_TOTAL) {
            val count = cursor.u16()
            counts[cell] = count
            offsets[cell] = cursor.pos // レコード先頭（count の直後）
            for (i in 0 until count) {
                cursor.skipVaruint() // townDelta
                cursor.skipVaruint() // packedCoord
                val flags = payload[cursor.pos++].toInt()
                skipPointTail(cursor, flags)
            }
        }
        offsets[CELLS_PER_BLOCK_TOTAL] = cursor.pos
        val block = PointBlock(offsets, counts, payload)
        if (pointBlockCache.size > MAX_CACHED_BLOCKS) pointBlockCache.clear()
        pointBlockCache[k] = block
        return block
    }

    private fun skipPointTail(cursor: Cursor, flags: Int) {
        if (flags and FLAG_BLOCK_LEVEL != 0) {
            if (flags and FLAG_BLOCK_TEXT != 0) {
                cursor.skipVaruint()
            } else {
                cursor.skipVaruint()
                cursor.skipVaruint()
            }
        }
        if (flags and FLAG_HAS_AZA != 0) cursor.skipVaruint()
    }

    private fun cellPoints(block: PointBlock, cellIdx: Int, cellLat: Int, cellLon: Int): List<RawPoint> {
        val count = block.cellCounts[cellIdx]
        if (count == 0) return emptyList()
        val cursor = Cursor(block.payload, block.cellOffsets[cellIdx])
        val result = ArrayList<RawPoint>(count)
        val baseLat = cellLat / 100.0
        val baseLon = cellLon / 100.0
        var townIdx = 0
        for (i in 0 until count) {
            townIdx += cursor.varuint()
            val packed = cursor.varuint()
            val flags = block.payload[cursor.pos++].toInt()
            var blockMain = 0
            var blockSub = 0
            var blockStr = 0
            var aza = 0
            if (flags and FLAG_BLOCK_LEVEL != 0) {
                if (flags and FLAG_BLOCK_TEXT != 0) {
                    blockStr = cursor.varuint()
                } else {
                    blockMain = cursor.varuint()
                    blockSub = cursor.varuint()
                }
            }
            if (flags and FLAG_HAS_AZA != 0) aza = cursor.varuint()
            result.add(
                RawPoint(
                    baseLat + (packed / 1024) / 100_000.0,
                    baseLon + (packed % 1024) / 100_000.0,
                    townIdx, flags, blockMain, blockSub, blockStr, aza,
                ),
            )
        }
        return result
    }

    // ---------- 道路ブロック ----------
    private fun roadBlock(blockLat: Int, blockLon: Int): RoadBlock? {
        val k = key(blockLat, blockLon)
        roadBlockCache[k]?.let { return it }
        val entry = roadDir[k] ?: return null
        val payload = inflate(entry)
        val offsets = IntArray(CELLS_PER_BLOCK_TOTAL + 1)
        val counts = IntArray(CELLS_PER_BLOCK_TOTAL)
        val cursor = Cursor(payload, 0)
        for (cell in 0 until CELLS_PER_BLOCK_TOTAL) {
            val count = cursor.u16()
            counts[cell] = count
            offsets[cell] = cursor.pos
            for (i in 0 until count) cursor.skipVaruint()
        }
        offsets[CELLS_PER_BLOCK_TOTAL] = cursor.pos
        val block = RoadBlock(offsets, counts, payload, cursor.pos)
        if (roadBlockCache.size > MAX_CACHED_BLOCKS) roadBlockCache.clear()
        roadBlockCache[k] = block
        return block
    }

    private fun roadSegments(block: RoadBlock, blockLat: Int, blockLon: Int): List<Pair<Int, IntArray>> {
        val cursor = Cursor(block.payload, block.segTableOff)
        val segCount = cursor.u32()
        val originLatE5 = blockLat * CELLS_PER_BLOCK * CELL_E5
        val originLonE5 = blockLon * CELLS_PER_BLOCK * CELL_E5
        val result = ArrayList<Pair<Int, IntArray>>(segCount)
        for (i in 0 until segCount) {
            val nameIdx = cursor.varuint()
            val pointCount = 2 // セグメントは常に2点
            val coords = IntArray(pointCount * 2)
            var prevLat = 0
            var prevLon = 0
            for (j in 0 until pointCount) {
                val dLat = cursor.zigzag()
                val dLon = cursor.zigzag()
                val latE5: Int
                val lonE5: Int
                if (j == 0) {
                    latE5 = originLatE5 + dLat
                    lonE5 = originLonE5 + dLon
                } else {
                    latE5 = prevLat + dLat
                    lonE5 = prevLon + dLon
                }
                coords[j * 2] = latE5
                coords[j * 2 + 1] = lonE5
                prevLat = latE5
                prevLon = lonE5
            }
            result.add(nameIdx to coords)
        }
        return result
    }

    // ---------- 検索 ----------
    fun lookup(latitude: Double, longitude: Double): AddressHit? {
        val blockHit = nearestPoint(latitude, longitude, blockOnly = true, maxRadiusMeters = BLOCK_SEARCH_RADIUS_METERS)
        val hit = blockHit ?: nearestPoint(latitude, longitude, blockOnly = false, maxRadiusMeters = ANY_SEARCH_RADIUS_METERS)
            ?: return null
        val (dist, point) = hit
        val (pref, muni, town) = townStrings(point.townIdx)
        val blockRaw = if (point.flags and FLAG_BLOCK_LEVEL != 0) {
            if (point.flags and FLAG_BLOCK_TEXT != 0) {
                strings[point.blockStrIdx]
            } else {
                buildString {
                    append(point.blockMain)
                    if (point.blockSub != 0) {
                        append('-')
                        append(point.blockSub)
                    }
                }
            }
        } else {
            null
        }
        return AddressHit(
            prefecture = pref,
            municipality = muni,
            townRaw = town,
            aza = if (point.flags and FLAG_HAS_AZA != 0) strings[point.azaStrIdx] else null,
            blockRaw = blockRaw,
            jyukyo = if (point.flags and FLAG_BLOCK_LEVEL != 0) point.flags and FLAG_JYUKYO != 0 else null,
            blockLevel = point.flags and FLAG_BLOCK_LEVEL != 0,
            distanceMeters = dist,
        )
    }

    private fun nearestPoint(
        latitude: Double,
        longitude: Double,
        blockOnly: Boolean,
        maxRadiusMeters: Int,
    ): Pair<Double, RawPoint>? {
        var best: Pair<Double, RawPoint>? = null
        for (radius in SEARCH_RADII_METERS) {
            val r = minOf(radius, maxRadiusMeters)
            val cells = cellsInCircle(latitude, longitude, r.toDouble())
            val byBlock = cells.groupBy { (it.first / CELLS_PER_BLOCK) to (it.second / CELLS_PER_BLOCK) }
            for ((blockKey, blockCells) in byBlock) {
                val block = pointBlock(blockKey.first, blockKey.second) ?: continue
                for ((cellLat, cellLon) in blockCells) {
                    val cellIdx = (cellLat - blockKey.first * CELLS_PER_BLOCK) * CELLS_PER_BLOCK +
                        (cellLon - blockKey.second * CELLS_PER_BLOCK)
                    for (point in cellPoints(block, cellIdx, cellLat, cellLon)) {
                        if (blockOnly && point.flags and FLAG_BLOCK_LEVEL == 0) continue
                        val dist = distanceMeters(latitude, longitude, point.lat, point.lon)
                        if (best == null || dist < best.first) best = dist to point
                    }
                }
            }
            if (best != null && best.first <= r) return best
            if (r >= maxRadiusMeters) break
        }
        return best?.takeIf { it.first <= maxRadiusMeters }
    }

    fun nearestRoadName(latitude: Double, longitude: Double, maxMeters: Double = DEFAULT_ROAD_RADIUS_METERS): String? {
        var best: Pair<Double, String>? = null
        val cells = cellsInCircle(latitude, longitude, maxMeters)
        val byBlock = cells.groupBy { (it.first / CELLS_PER_BLOCK) to (it.second / CELLS_PER_BLOCK) }
        for ((blockKey, blockCells) in byBlock) {
            val block = roadBlock(blockKey.first, blockKey.second) ?: continue
            val wantedSegs = HashSet<Int>()
            for ((cellLat, cellLon) in blockCells) {
                val cellIdx = (cellLat - blockKey.first * CELLS_PER_BLOCK) * CELLS_PER_BLOCK +
                    (cellLon - blockKey.second * CELLS_PER_BLOCK)
                val cursor = Cursor(block.payload, block.cellOffsets[cellIdx])
                repeat(block.cellCounts[cellIdx]) { wantedSegs.add(cursor.varuint()) }
            }
            if (wantedSegs.isEmpty()) continue
            val segments = roadSegments(block, blockKey.first, blockKey.second)
            for (segIdx in wantedSegs) {
                if (segIdx !in segments.indices) continue
                val (nameIdx, coords) = segments[segIdx]
                var i = 0
                while (i + 3 < coords.size) {
                    val dist = segmentDistanceMeters(
                        latitude, longitude,
                        coords[i] / 100_000.0, coords[i + 1] / 100_000.0,
                        coords[i + 2] / 100_000.0, coords[i + 3] / 100_000.0,
                    )
                    if (best == null || dist < best.first) best = dist to strings[nameIdx]
                    i += 2
                }
            }
        }
        return best?.takeIf { it.first <= maxMeters }?.second
    }

    private fun cellsInCircle(latitude: Double, longitude: Double, radiusMeters: Double): List<Pair<Int, Int>> {
        val dLat = Math.toDegrees(radiusMeters / EARTH_RADIUS_METERS)
        val cosLat = cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
        val dLon = Math.toDegrees(radiusMeters / (EARTH_RADIUS_METERS * cosLat))
        val latFrom = Math.floor((latitude - dLat) * 100).toInt()
        val latTo = Math.floor((latitude + dLat) * 100).toInt()
        val lonFrom = Math.floor((longitude - dLon) * 100).toInt()
        val lonTo = Math.floor((longitude + dLon) * 100).toInt()
        val result = ArrayList<Pair<Int, Int>>()
        for (cellLat in latFrom..latTo) {
            for (cellLon in lonFrom..lonTo) {
                result.add(cellLat to cellLon)
            }
        }
        return result
    }

    private fun key(blockLat: Int, blockLon: Int): Long =
        (blockLat.toLong() shl 32) or (blockLon.toLong() and 0xFFFFFFFFL)

    companion object {
        private const val MAGIC = "TJADB002"
        private const val FORMAT_VERSION = 2
        private const val HEADER_SIZE = 128
        private const val DIR_ENTRY_SIZE = 20
        private const val CELLS_PER_BLOCK = 8
        private const val CELLS_PER_BLOCK_TOTAL = 64
        private const val CELL_E5 = 1000
        private const val FLAG_BLOCK_LEVEL = 0b0001
        private const val FLAG_JYUKYO = 0b0010
        private const val FLAG_HAS_AZA = 0b0100
        private const val FLAG_BLOCK_TEXT = 0b1000
        private const val EARTH_RADIUS_METERS = 6371000.0
        private const val BLOCK_SEARCH_RADIUS_METERS = 1000
        private const val ANY_SEARCH_RADIUS_METERS = 20000
        private const val DEFAULT_ROAD_RADIUS_METERS = 40.0
        private const val MAX_CACHED_BLOCKS = 6
        private val SEARCH_RADII_METERS = intArrayOf(200, 500, 1000, 2500, 6000, 20000)

        fun load(bytes: ByteArray): AddressDatabase? = try {
            AddressDatabase(bytes)
        } catch (_: Exception) {
            null
        }

        internal fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dp = Math.toRadians(lat2 - lat1)
            val dl = Math.toRadians(lon2 - lon1)
            val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
            return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
        }

        internal fun segmentDistanceMeters(
            latitude: Double,
            longitude: Double,
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double,
        ): Double {
            val kx = EARTH_RADIUS_METERS * cos(Math.toRadians(latitude))
            val ky = EARTH_RADIUS_METERS
            val ax = Math.toRadians(lon1) * kx
            val ay = Math.toRadians(lat1) * ky
            val bx = Math.toRadians(lon2) * kx
            val by = Math.toRadians(lat2) * ky
            val px = Math.toRadians(longitude) * kx
            val py = Math.toRadians(latitude) * ky
            val dx = bx - ax
            val dy = by - ay
            val denom = dx * dx + dy * dy
            val t = if (denom == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / denom).coerceIn(0.0, 1.0)
            return hypot(px - (ax + t * dx), py - (ay + t * dy))
        }
    }
}

private val CHOME_PATTERN = Regex("^(.+?)([0-9０-９]+|[一二三四五六七八九十]+)丁目$")

/** 町名（例: 毛馬町三丁目）を (町名, ○丁目) に分離する。丁目は算用数字に正規化する。 */
internal fun splitTownChome(townRaw: String): Pair<String, String?> {
    val match = CHOME_PATTERN.matchEntire(townRaw) ?: return townRaw to null
    val town = match.groupValues[1]
    val numeral = match.groupValues[2]
    val number = numeralToArabic(numeral) ?: return townRaw to null
    return town to "${number}丁目"
}

internal fun numeralToArabic(numeral: String): Int? {
    val halfwidth = numeral.map { ch ->
        if (ch in '０'..'９') ('0'.code + (ch.code - '０'.code)).toChar() else ch
    }.joinToString("")
    halfwidth.toIntOrNull()?.let { return it }
    val digits = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
    if (halfwidth == "十") return 10
    val tenIndex = halfwidth.indexOf('十')
    if (tenIndex >= 0) {
        val tensPart = halfwidth.substring(0, tenIndex)
        val onesPart = halfwidth.substring(tenIndex + 1)
        val tens = if (tensPart.isEmpty()) 1 else digits[tensPart.singleOrNull() ?: return null] ?: return null
        val ones = if (onesPart.isEmpty()) 0 else digits[onesPart.singleOrNull() ?: return null] ?: return null
        return tens * 10 + ones
    }
    return digits[halfwidth.singleOrNull() ?: return null]
}

/**
 * オフラインDBのヒット結果から表示用住所を合成する。
 * 住居表示区域: 市区町村＋町＋丁目＋街区符号＋「番」
 * 地番区域:     市区町村＋町＋丁目＋小字＋地番（原表記）＋「番地」
 */
internal fun buildOfflineAddress(hit: AddressDatabase.AddressHit, roadName: String?): ReverseGeocodeAddress {
    val (town, chome) = splitTownChome(hit.townRaw)
    val areaText = buildString {
        append(hit.municipality)
        append(town)
        chome?.let { append(it) }
    }
    val reportAddress = buildString {
        append(areaText)
        if (hit.blockLevel && hit.blockRaw != null) {
            if (hit.jyukyo == true) {
                append(hit.blockRaw)
                append("番")
            } else {
                hit.aza?.let { append(it) }
                append(hit.blockRaw)
                if (hit.blockRaw.lastOrNull()?.isDigit() == true) append("番地")
            }
        }
    }
    return ReverseGeocodeAddress(
        canonicalAddress = hit.prefecture + reportAddress,
        reportAddress = reportAddress,
        featureName = null,
        featureDetail = null,
        roadName = roadName,
        areaText = areaText,
        municipality = hit.municipality,
        town = town,
        chome = chome,
        aza = hit.aza,
        block = hit.blockRaw,
        houseNumber = null,
    )
}
