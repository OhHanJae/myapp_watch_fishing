package com.eastsea.badahannun.domain

import com.eastsea.badahannun.data.FishingPoint

/** Watch-first display order; does not change the underlying fishing-point catalog. */
object PointSelection {
    private val regionPriority = listOf("울산", "경주", "포항", "부산")
    private val ulsanPriority = listOf("ulsan-seuldo", "ulsan-jujeon", "ulsan-dangsa")

    fun regions(points: List<FishingPoint>): List<String> {
        val known = points.map { it.region }.distinct()
        return regionPriority.filter { it in known } + known.filter { it !in regionPriority }
    }

    fun inRegion(points: List<FishingPoint>, region: String): List<FishingPoint> {
        val matches = points.filter { it.region == region }
        if (region != "울산") return matches
        return matches.sortedWith(compareBy { point ->
            ulsanPriority.indexOf(point.id).takeIf { it >= 0 } ?: Int.MAX_VALUE
        })
    }
}
