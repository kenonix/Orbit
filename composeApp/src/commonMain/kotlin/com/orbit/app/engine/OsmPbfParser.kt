package com.orbit.app.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.decodeFromStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.Inflater

@Serializable
data class OsmCoord(val lat: Double, val lon: Double)

@Serializable
data class OsmPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
    val type: String,
    val tags: Map<String, String> = emptyMap()
)

@Serializable
data class OsmWay(
    val id: Long,
    val name: String,
    val type: String,
    val coords: List<OsmCoord>,
    val tags: Map<String, String> = emptyMap(),
    val minLat: Double = 0.0,
    val maxLat: Double = 0.0,
    val minLon: Double = 0.0,
    val maxLon: Double = 0.0
)

@Serializable
data class OsmMapData(
    val places: List<OsmPlace>,
    val ways: List<OsmWay>
)

object OsmPbfParser {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    // Save map data to local GZIP compressed JSON cache
    @OptIn(ExperimentalSerializationApi::class)
    fun saveToCache(cacheFile: File, mapData: OsmMapData) {
        try {
            FileOutputStream(cacheFile).use { fos ->
                BufferedOutputStream(fos).use { bos ->
                    GZIPOutputStream(bos).use { gzos ->
                        json.encodeToStream(OsmMapData.serializer(), mapData, gzos)
                    }
                }
            }
        } catch (e: Exception) {
            println("Failed to write map cache: ${e.message}")
            e.printStackTrace()
        }
    }

    // Load map data from local GZIP compressed JSON cache
    @OptIn(ExperimentalSerializationApi::class)
    fun loadFromCache(cacheFile: File): OsmMapData {
        return FileInputStream(cacheFile).use { fis ->
            BufferedInputStream(fis).use { bis ->
                GZIPInputStream(bis).use { gzis ->
                    json.decodeFromStream(OsmMapData.serializer(), gzis)
                }
            }
        }
    }

    // Main parsing function
    suspend fun parsePbf(file: File): OsmMapData = coroutineScope {
        val referencedNodes = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
        val places = java.util.Collections.synchronizedList(ArrayList<OsmPlace>())
        val nodeCoords = java.util.concurrent.ConcurrentHashMap<Long, OsmCoord>()
        val ways = java.util.Collections.synchronizedList(ArrayList<OsmWay>())

        val blocks = ArrayList<ByteArray>()
        // Read file once and load OSMData blocks sequentially
        FileInputStream(file).use { fis ->
            val bis = BufferedInputStream(fis)
            while (true) {
                val lengthBytes = ByteArray(4)
                val read = bis.read(lengthBytes)
                if (read < 4) break
                val length = ((lengthBytes[0].toInt() and 0xFF) shl 24) or
                             ((lengthBytes[1].toInt() and 0xFF) shl 16) or
                             ((lengthBytes[2].toInt() and 0xFF) shl 8) or
                             (lengthBytes[3].toInt() and 0xFF)

                val headerBytes = ByteArray(length)
                var bytesRead = 0
                while (bytesRead < length) {
                    val r = bis.read(headerBytes, bytesRead, length - bytesRead)
                    if (r < 0) break
                    bytesRead += r
                }
                if (bytesRead < length) break

                val (type, datasize) = parseBlobHeader(headerBytes)

                val blobBytes = ByteArray(datasize)
                var blobBytesRead = 0
                while (blobBytesRead < datasize) {
                    val r = bis.read(blobBytes, blobBytesRead, datasize - blobBytesRead)
                    if (r < 0) break
                    blobBytesRead += r
                }
                if (blobBytesRead < datasize) break

                if (type == "OSMData") {
                    blocks.add(blobBytes)
                }
            }
        }

        val semaphore = Semaphore(32)

        // Pass 1: Parallel block decompression and highway ref extraction
        blocks.map { blobBytes ->
            async(Dispatchers.Default) {
                semaphore.withPermit {
                    try {
                        val rawData = parseBlob(blobBytes)
                        parseBlockForRefs(rawData, referencedNodes)
                    } catch (e: Exception) {
                        println("Error parsing block in pass 1: ${e.message}")
                    }
                }
            }
        }.awaitAll()

        // Pass 2: Extract coordinates for referenced nodes and named places in parallel
        blocks.map { blobBytes ->
            async(Dispatchers.Default) {
                semaphore.withPermit {
                    try {
                        val rawData = parseBlob(blobBytes)
                        parseNodesAndPlaces(rawData, referencedNodes, nodeCoords, places)
                    } catch (e: Exception) {
                        println("Error parsing block in pass 2: ${e.message}")
                    }
                }
            }
        }.awaitAll()

        // Pass 3: Reconstruct way geometries in parallel using collected coordinates
        blocks.map { blobBytes ->
            async(Dispatchers.Default) {
                semaphore.withPermit {
                    try {
                        val rawData = parseBlob(blobBytes)
                        parseWaysAndBuildGeometry(rawData, nodeCoords, ways, places)
                    } catch (e: Exception) {
                        println("Error parsing block in pass 3: ${e.message}")
                    }
                }
            }
        }.awaitAll()

        OsmMapData(places.toList(), ways.toList())
    }

    private fun parseBlobHeader(bytes: ByteArray): Pair<String, Int> {
        val reader = ProtoReader(bytes)
        var type = ""
        var datasize = 0
        while (reader.hasMore()) {
            val tag = reader.readVarint32()
            val fieldNum = tag shr 3
            val wireType = tag and 0x07
            when (fieldNum) {
                1 -> type = reader.readString()
                3 -> datasize = reader.readVarint32()
                else -> reader.skipField(wireType)
            }
        }
        return Pair(type, datasize)
    }

    private fun parseBlob(bytes: ByteArray): ByteArray {
        val reader = ProtoReader(bytes)
        var rawBytes: ByteArray? = null
        var zlibBytes: ByteArray? = null
        while (reader.hasMore()) {
            val tag = reader.readVarint32()
            val fieldNum = tag shr 3
            val wireType = tag and 0x07
            when (fieldNum) {
                1 -> rawBytes = reader.readBytes()
                3 -> zlibBytes = reader.readBytes()
                else -> reader.skipField(wireType)
            }
        }
        if (rawBytes != null) return rawBytes
        if (zlibBytes != null) {
            val inflater = Inflater()
            inflater.setInput(zlibBytes)
            val outStream = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0) break
                outStream.write(buffer, 0, count)
            }
            inflater.end()
            return outStream.toByteArray()
        }
        throw Exception("No data in blob")
    }

    private fun parseStringTable(bytes: ByteArray): List<String> {
        val reader = ProtoReader(bytes)
        val list = ArrayList<String>()
        while (reader.hasMore()) {
            val tag = reader.readVarint32()
            val fieldNum = tag shr 3
            val wireType = tag and 0x07
            if (fieldNum == 1) {
                list.add(reader.readString())
            } else {
                reader.skipField(wireType)
            }
        }
        return list
    }

    private fun parseBlockForRefs(bytes: ByteArray, referencedNodes: MutableSet<Long>) {
        val blockReader = ProtoReader(bytes)
        var stringTable: List<String> = emptyList()
        val groups = ArrayList<ByteArray>()
        while (blockReader.hasMore()) {
            val tag = blockReader.readVarint32()
            val fieldNum = tag shr 3
            val wireType = tag and 0x07
            when (fieldNum) {
                1 -> stringTable = parseStringTable(blockReader.readBytes())
                2 -> groups.add(blockReader.readBytes())
                else -> blockReader.skipField(wireType)
            }
        }

        for (groupBytes in groups) {
            val groupReader = ProtoReader(groupBytes)
            while (groupReader.hasMore()) {
                val gTag = groupReader.readVarint32()
                val gFieldNum = gTag shr 3
                val gWireType = gTag and 0x07
                if (gFieldNum == 3) { // way
                    val wayBytes = groupReader.readBytes()
                    val wayReader = ProtoReader(wayBytes)
                    var keys: List<Int> = emptyList()
                    var refs: List<Long> = emptyList()
                    while (wayReader.hasMore()) {
                        val wTag = wayReader.readVarint32()
                        val wFieldNum = wTag shr 3
                        val wWireType = wTag and 0x07
                        when (wFieldNum) {
                            2 -> keys = readPackedVarints(wayReader.readBytes()).map { it.toInt() }
                            8 -> refs = readPackedSints(wayReader.readBytes())
                            else -> wayReader.skipField(wWireType)
                        }
                    }

                    var isHighway = false
                    for (kIdx in keys) {
                        val kStr = stringTable.getOrNull(kIdx)
                        if (kStr == "highway") {
                            isHighway = true
                            break
                        }
                    }
                    if (isHighway && refs.isNotEmpty()) {
                        var currentRef = 0L
                        for (delta in refs) {
                            currentRef += delta
                            referencedNodes.add(currentRef)
                        }
                    }
                } else {
                    groupReader.skipField(gWireType)
                }
            }
        }
    }

    private fun parseNodesAndPlaces(
        bytes: ByteArray,
        referencedNodes: MutableSet<Long>,
        nodeCoords: MutableMap<Long, OsmCoord>,
        places: MutableList<OsmPlace>
    ) {
        val blockReader = ProtoReader(bytes)
        var stringTable: List<String> = emptyList()
        var granularity = 100
        var latOffset = 0L
        var lonOffset = 0L
        val groups = ArrayList<ByteArray>()

        while (blockReader.hasMore()) {
            val tag = blockReader.readVarint32()
            val fieldNum = tag shr 3
            val wireType = tag and 0x07
            when (fieldNum) {
                1 -> stringTable = parseStringTable(blockReader.readBytes())
                2 -> groups.add(blockReader.readBytes())
                17 -> granularity = blockReader.readVarint32()
                19 -> latOffset = blockReader.readVarint64()
                20 -> lonOffset = blockReader.readVarint64()
                else -> blockReader.skipField(wireType)
            }
        }

        for (groupBytes in groups) {
            val groupReader = ProtoReader(groupBytes)
            while (groupReader.hasMore()) {
                val gTag = groupReader.readVarint32()
                val gFieldNum = gTag shr 3
                val gWireType = gTag and 0x07
                if (gFieldNum == 2) { // dense
                    val denseBytes = groupReader.readBytes()
                    val denseReader = ProtoReader(denseBytes)
                    var ids: List<Long> = emptyList()
                    var lats: List<Long> = emptyList()
                    var lons: List<Long> = emptyList()
                    var keysVals: List<Int> = emptyList()

                    while (denseReader.hasMore()) {
                        val dTag = denseReader.readVarint32()
                        val dFieldNum = dTag shr 3
                        val dWireType = dTag and 0x07
                        when (dFieldNum) {
                            1 -> ids = readPackedSints(denseReader.readBytes())
                            8 -> lats = readPackedSints(denseReader.readBytes())
                            9 -> lons = readPackedSints(denseReader.readBytes())
                            10 -> keysVals = readPackedVarints(denseReader.readBytes()).map { it.toInt() }
                            else -> denseReader.skipField(dWireType)
                        }
                    }

                    var currentId = 0L
                    var currentLat = 0L
                    var currentLon = 0L
                    var tagIdx = 0

                    for (i in ids.indices) {
                        currentId += ids[i]
                        currentLat += lats[i]
                        currentLon += lons[i]

                        val latDeg = (latOffset + granularity * currentLat) * 1e-9
                        val lonDeg = (lonOffset + granularity * currentLon) * 1e-9

                        // Extract tags
                        val tags = HashMap<String, String>()
                        if (tagIdx < keysVals.size) {
                            while (tagIdx < keysVals.size) {
                                val key = keysVals[tagIdx++]
                                if (key == 0) break
                                val value = keysVals[tagIdx++]
                                val kStr = stringTable.getOrNull(key) ?: ""
                                val vStr = stringTable.getOrNull(value) ?: ""
                                tags[kStr] = vStr
                            }
                        }

                        if (referencedNodes.contains(currentId)) {
                            nodeCoords[currentId] = OsmCoord(latDeg, lonDeg)
                        }

                        val name = tags["name"] ?: tags["name:ko"] ?: tags["name:en"]
                        val place = tags["place"] ?: tags["amenity"] ?: tags["shop"] ?: tags["tourism"] ?: tags["building"] ?: tags["highway"]
                        if (name != null && place != null) {
                            places.add(OsmPlace(name, latDeg, lonDeg, place, tags))
                        }
                    }
                } else {
                    groupReader.skipField(gWireType)
                }
            }
        }
    }

    private fun parseWaysAndBuildGeometry(
        bytes: ByteArray,
        nodeCoords: MutableMap<Long, OsmCoord>,
        ways: MutableList<OsmWay>,
        places: MutableList<OsmPlace>
    ) {
        val blockReader = ProtoReader(bytes)
        var stringTable: List<String> = emptyList()
        val groups = ArrayList<ByteArray>()

        while (blockReader.hasMore()) {
            val tag = blockReader.readVarint32()
            val fieldNum = tag shr 3
            val wireType = tag and 0x07
            when (fieldNum) {
                1 -> stringTable = parseStringTable(blockReader.readBytes())
                2 -> groups.add(blockReader.readBytes())
                else -> blockReader.skipField(wireType)
            }
        }

        for (groupBytes in groups) {
            val groupReader = ProtoReader(groupBytes)
            while (groupReader.hasMore()) {
                val gTag = groupReader.readVarint32()
                val gFieldNum = gTag shr 3
                val gWireType = gTag and 0x07
                if (gFieldNum == 3) { // way
                    val wayBytes = groupReader.readBytes()
                    val wayReader = ProtoReader(wayBytes)
                    var id = 0L
                    var keys: List<Int> = emptyList()
                    var vals: List<Int> = emptyList()
                    var refs: List<Long> = emptyList()

                    while (wayReader.hasMore()) {
                        val wTag = wayReader.readVarint32()
                        val wFieldNum = wTag shr 3
                        val wWireType = wTag and 0x07
                        when (wFieldNum) {
                            1 -> id = wayReader.readVarint64()
                            2 -> keys = readPackedVarints(wayReader.readBytes()).map { it.toInt() }
                            3 -> vals = readPackedVarints(wayReader.readBytes()).map { it.toInt() }
                            8 -> refs = readPackedSints(wayReader.readBytes())
                            else -> wayReader.skipField(wWireType)
                        }
                    }

                    val tags = HashMap<String, String>()
                    for (idx in keys.indices) {
                        val k = stringTable.getOrNull(keys[idx]) ?: ""
                        val v = stringTable.getOrNull(vals[idx]) ?: ""
                        tags[k] = v
                    }

                    val highway = tags["highway"]
                    val name = tags["name"] ?: tags["name:ko"] ?: tags["name:en"]

                    val coords = ArrayList<OsmCoord>()
                    var currentRef = 0L
                    for (delta in refs) {
                        currentRef += delta
                        val coord = nodeCoords[currentRef]
                        if (coord != null) {
                            coords.add(coord)
                        }
                    }
                    if (coords.isNotEmpty()) {
                        var minLat = Double.MAX_VALUE
                        var maxLat = -Double.MAX_VALUE
                        var minLon = Double.MAX_VALUE
                        var maxLon = -Double.MAX_VALUE
                        for (i in 0 until coords.size) {
                            val c = coords[i]
                            if (c.lat < minLat) minLat = c.lat
                            if (c.lat > maxLat) maxLat = c.lat
                            if (c.lon < minLon) minLon = c.lon
                            if (c.lon > maxLon) maxLon = c.lon
                        }

                        val isBuilding = tags.containsKey("building")
                        val isWater = tags.containsKey("water") || tags["natural"] == "water" || tags["waterway"] != null
                        if (highway != null || isBuilding || isWater) {
                            val type = highway ?: if (isBuilding) "building" else "water"
                            ways.add(OsmWay(id, name ?: "", type, coords, tags, minLat, maxLat, minLon, maxLon))
                        }

                        if (name != null) {
                            val avgLat = (minLat + maxLat) / 2.0
                            val avgLon = (minLon + maxLon) / 2.0
                            val placeType = tags["place"] ?: tags["amenity"] ?: tags["shop"] ?: tags["tourism"] ?: tags["building"]
                            if (placeType != null) {
                                places.add(OsmPlace(name, avgLat, avgLon, placeType, tags))
                            } else if (highway != null) {
                                places.add(OsmPlace(name, avgLat, avgLon, "road", tags))
                            }
                        }
                    }
                } else {
                    groupReader.skipField(gWireType)
                }
            }
        }
    }

    private fun readPackedVarints(bytes: ByteArray): List<Long> {
        val reader = ProtoReader(bytes)
        val list = ArrayList<Long>()
        while (reader.hasMore()) {
            list.add(reader.readVarint64())
        }
        return list
    }

    private fun readPackedSints(bytes: ByteArray): List<Long> {
        val reader = ProtoReader(bytes)
        val list = ArrayList<Long>()
        while (reader.hasMore()) {
            val raw = reader.readVarint64()
            val decoded = (raw ushr 1) xor -(raw and 1)
            list.add(decoded)
        }
        return list
    }
}

class ProtoReader(val buffer: ByteArray, var offset: Int = 0, val limit: Int = buffer.size) {
    fun hasMore() = offset < limit

    fun readVarint64(): Long {
        var result: Long = 0
        var shift = 0
        while (shift < 64) {
            if (offset >= limit) throw java.io.EOFException("EOF in varint")
            val b = buffer[offset++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
        throw Exception("Malformed varint")
    }

    fun readVarint32(): Int = readVarint64().toInt()

    fun skipField(wireType: Int) {
        when (wireType) {
            0 -> readVarint64()
            1 -> offset += 8
            2 -> {
                val len = readVarint32()
                offset += len
            }
            5 -> offset += 4
            else -> throw Exception("Unknown wire type $wireType")
        }
    }

    fun readBytes(): ByteArray {
        val len = readVarint32()
        if (offset + len > limit) throw java.io.EOFException("EOF in bytes field")
        val bytes = ByteArray(len)
        System.arraycopy(buffer, offset, bytes, 0, len)
        offset += len
        return bytes
    }

    fun readString(): String {
        val len = readVarint32()
        if (offset + len > limit) throw java.io.EOFException("EOF in string field")
        val s = String(buffer, offset, len, Charsets.UTF_8)
        offset += len
        return s
    }
}

object SimulatedMapData {
    val data = OsmMapData(
        places = listOf(
            OsmPlace("서울역 (Seoul Station)", 37.5559, 126.9723, "station", emptyMap()),
            OsmPlace("경복궁 (Gyeongbokgung Palace)", 37.5796, 126.9770, "attraction", emptyMap()),
            OsmPlace("N서울타워 (N Seoul Tower)", 37.5511, 126.9882, "tourism", emptyMap()),
            OsmPlace("명동 (Myeongdong)", 37.5599, 126.9858, "suburb", emptyMap()),
            OsmPlace("인사동 (Insadong)", 37.5746, 126.9882, "suburb", emptyMap()),
            OsmPlace("서울시청 (Seoul City Hall)", 37.5665, 126.9780, "city_hall", emptyMap()),
            OsmPlace("광화문광장 (Gwanghwamun Square)", 37.5728, 126.9769, "park", emptyMap())
        ),
        ways = listOf(
            OsmWay(
                id = 1L,
                name = "세종대로",
                type = "primary",
                coords = listOf(
                    OsmCoord(37.5559, 126.9723), // Seoul Station
                    OsmCoord(37.5665, 126.9780), // City Hall
                    OsmCoord(37.5728, 126.9769), // Gwanghwamun Square
                    OsmCoord(37.5796, 126.9770)  // Gyeongbokgung
                ),
                tags = mapOf("name" to "세종대로")
            ),
            OsmWay(
                id = 2L,
                name = "종로",
                type = "primary",
                coords = listOf(
                    OsmCoord(37.5728, 126.9769),
                    OsmCoord(37.5728, 126.9850),
                    OsmCoord(37.5728, 126.9920)
                ),
                tags = mapOf("name" to "종로")
            ),
            OsmWay(
                id = 3L,
                name = "을지로",
                type = "secondary",
                coords = listOf(
                    OsmCoord(37.5665, 126.9780),
                    OsmCoord(37.5660, 126.9850),
                    OsmCoord(37.5655, 126.9920)
                ),
                tags = mapOf("name" to "을지로")
            ),
            OsmWay(
                id = 4L,
                name = "명동길",
                type = "residential",
                coords = listOf(
                    OsmCoord(37.5599, 126.9858),
                    OsmCoord(37.5630, 126.9850)
                ),
                tags = mapOf("name" to "명동길")
            )
        )
    )
}
