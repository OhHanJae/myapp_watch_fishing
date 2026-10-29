package com.eastsea.badahannun.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Original fishing catalogue from lure_guide.html.
 * Only data is imported. The original website/JavaScript is not executed on the watch.
 */
data class FishingPoint(
    val id: String,
    val region: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val types: List<String>,
    val fishNames: List<String>,
    val note: String,
    val direction: String
)

data class FishSpecies(
    val id: String,
    val name: String,
    val months: Set<Int>,
    val peakMonths: Set<Int>,
    val times: Set<String>,
    val types: Set<String>,
    val lureMinGram: Double,
    val lureMaxGram: Double,
    val mainGear: Int,
    val subGear: Int,
    val lures: List<String>,
    val colors: List<String>,
    val action: String,
    val water: String,
    val note: String
)

data class FishingGear(
    val id: Int,
    val shortName: String,
    val fullName: String,
    val role: String
)

data class GuideCatalog(
    val points: List<FishingPoint>,
    val fish: List<FishSpecies>,
    val gears: List<FishingGear>
) {
    companion object {
        fun fromAssets(context: Context): GuideCatalog {
            val jsonText = context.assets.open("guide_data.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(jsonText)

            val pointList = root.getJSONArray("points").objects().map { p ->
                FishingPoint(
                    id = p.getString("id"),
                    region = p.getString("region"),
                    name = p.getString("name"),
                    latitude = p.getDouble("lat"),
                    longitude = p.getDouble("lon"),
                    types = p.getJSONArray("types").strings(),
                    fishNames = p.getJSONArray("fish").strings(),
                    note = p.optString("note"),
                    direction = p.optString("direction")
                )
            }

            val species = root.getJSONArray("fish").objects().map { f ->
                val range = f.getJSONArray("lureRange")
                FishSpecies(
                    id = f.getString("id"),
                    name = f.getString("name"),
                    months = f.getJSONArray("months").ints().toSet(),
                    peakMonths = f.getJSONArray("peak").ints().toSet(),
                    times = f.getJSONArray("times").strings().toSet(),
                    types = f.getJSONArray("types").strings().toSet(),
                    lureMinGram = range.getDouble(0),
                    lureMaxGram = range.getDouble(1),
                    mainGear = f.getInt("mainGear"),
                    subGear = f.getInt("subGear"),
                    lures = f.getJSONArray("lures").strings(),
                    colors = f.getJSONArray("colors").strings(),
                    action = f.optString("action"),
                    water = f.optString("water"),
                    note = f.optString("note")
                )
            }

            val gearList = root.getJSONArray("gears").objects().map { g ->
                FishingGear(
                    id = g.getInt("id"),
                    shortName = g.getString("short"),
                    fullName = g.getString("name"),
                    role = g.getString("role")
                )
            }

            return GuideCatalog(pointList, species, gearList)
        }
    }
}

private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
private fun JSONArray.strings() = (0 until length()).map { getString(it) }
private fun JSONArray.ints() = (0 until length()).map { getInt(it) }
