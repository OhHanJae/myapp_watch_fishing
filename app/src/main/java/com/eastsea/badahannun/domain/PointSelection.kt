package com.eastsea.badahannun.domain

import com.eastsea.badahannun.data.FishingPoint

/** Provides stable, tappable region and point lists for the small round screen. */
object PointSelection {
    fun regions(points: List<FishingPoint>): List<String> =
        points.map { it.region }.distinct()

    fun inRegion(points: List<FishingPoint>, region: String): List<FishingPoint> =
        points.filter { it.region == region }
}
