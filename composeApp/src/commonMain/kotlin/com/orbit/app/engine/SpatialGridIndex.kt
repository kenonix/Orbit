package com.orbit.app.engine

class SpatialGridIndex(
    val ways: List<OsmWay>,
    val places: List<OsmPlace>
) {
    private var minLat = Double.MAX_VALUE
    private var maxLat = -Double.MAX_VALUE
    private var minLon = Double.MAX_VALUE
    private var maxLon = -Double.MAX_VALUE

    private val rows = 128
    private val cols = 128

    // Grid cells storing lists of ways
    private val wayGrid: Array<Array<ArrayList<OsmWay>>>
    // Grid cells storing lists of places
    private val placeGrid: Array<Array<ArrayList<OsmPlace>>>

    init {
        // Calculate total bounds
        for (way in ways) {
            if (way.minLat < minLat) minLat = way.minLat
            if (way.maxLat > maxLat) maxLat = way.maxLat
            if (way.minLon < minLon) minLon = way.minLon
            if (way.maxLon > maxLon) maxLon = way.maxLon
        }
        for (place in places) {
            if (place.lat < minLat) minLat = place.lat
            if (place.lat > maxLat) maxLat = place.lat
            if (place.lon < minLon) minLon = place.lon
            if (place.lon > maxLon) maxLon = place.lon
        }

        // If bounds are invalid, set default bounds for South Korea
        if (minLat >= maxLat || minLon >= maxLon) {
            minLat = 33.0
            maxLat = 39.0
            minLon = 124.0
            maxLon = 131.0
        }

        // Initialize grid
        wayGrid = Array(rows) { Array(cols) { ArrayList<OsmWay>() } }
        placeGrid = Array(rows) { Array(cols) { ArrayList<OsmPlace>() } }

        val latRange = maxLat - minLat
        val lonRange = maxLon - minLon

        // Populate way grid
        for (way in ways) {
            val rStart = (((way.minLat - minLat) / latRange) * rows).toInt().coerceIn(0, rows - 1)
            val rEnd = (((way.maxLat - minLat) / latRange) * rows).toInt().coerceIn(0, rows - 1)
            val cStart = (((way.minLon - minLon) / lonRange) * cols).toInt().coerceIn(0, cols - 1)
            val cEnd = (((way.maxLon - minLon) / lonRange) * cols).toInt().coerceIn(0, cols - 1)

            for (r in rStart..rEnd) {
                for (c in cStart..cEnd) {
                    wayGrid[r][c].add(way)
                }
            }
        }

        // Populate place grid
        for (place in places) {
            val r = (((place.lat - minLat) / latRange) * rows).toInt().coerceIn(0, rows - 1)
            val c = (((place.lon - minLon) / lonRange) * cols).toInt().coerceIn(0, cols - 1)
            placeGrid[r][c].add(place)
        }
    }

    // Query ways in bounding box
    fun queryWays(qMinLat: Double, qMaxLat: Double, qMinLon: Double, qMaxLon: Double): List<OsmWay> {
        val latRange = maxLat - minLat
        val lonRange = maxLon - minLon

        val rStart = (((qMinLat - minLat) / latRange) * rows).toInt().coerceIn(0, rows - 1)
        val rEnd = (((qMaxLat - minLat) / latRange) * rows).toInt().coerceIn(0, rows - 1)
        val cStart = (((qMinLon - minLon) / lonRange) * cols).toInt().coerceIn(0, cols - 1)
        val cEnd = (((qMaxLon - minLon) / lonRange) * cols).toInt().coerceIn(0, cols - 1)

        val resultSet = HashSet<OsmWay>()
        for (r in rStart..rEnd) {
            for (c in cStart..cEnd) {
                val cellWays = wayGrid[r][c]
                for (i in 0 until cellWays.size) {
                    val way = cellWays[i]
                    // Refined Bounding Box overlap check
                    if (way.maxLat >= qMinLat && way.minLat <= qMaxLat &&
                        way.maxLon >= qMinLon && way.minLon <= qMaxLon) {
                        resultSet.add(way)
                    }
                }
            }
        }
        return resultSet.toList()
    }

    // Query places in bounding box
    fun queryPlaces(qMinLat: Double, qMaxLat: Double, qMinLon: Double, qMaxLon: Double): List<OsmPlace> {
        val latRange = maxLat - minLat
        val lonRange = maxLon - minLon

        val rStart = (((qMinLat - minLat) / latRange) * rows).toInt().coerceIn(0, rows - 1)
        val rEnd = (((qMaxLat - minLat) / latRange) * rows).toInt().coerceIn(0, rows - 1)
        val cStart = (((qMinLon - minLon) / lonRange) * cols).toInt().coerceIn(0, cols - 1)
        val cEnd = (((qMaxLon - minLon) / lonRange) * cols).toInt().coerceIn(0, cols - 1)

        val resultSet = HashSet<OsmPlace>()
        for (r in rStart..rEnd) {
            for (c in cStart..cEnd) {
                val cellPlaces = placeGrid[r][c]
                for (i in 0 until cellPlaces.size) {
                    val place = cellPlaces[i]
                    if (place.lat in qMinLat..qMaxLat && place.lon in qMinLon..qMaxLon) {
                        resultSet.add(place)
                    }
                }
            }
        }
        return resultSet.toList()
    }
}
