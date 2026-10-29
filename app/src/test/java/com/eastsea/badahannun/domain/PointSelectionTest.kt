package com.eastsea.badahannun.domain

import com.eastsea.badahannun.data.FishingPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class PointSelectionTest {
    private val points = listOf(
        point("a", "울산", "정자항"),
        point("b", "포항", "구룡포"),
        point("c", "울산", "슬도"),
        point("d", "부산", "기장")
    )

    @Test fun regionsPreserveCatalogOrderWithoutDuplicates() {
        assertEquals(listOf("울산", "포항", "부산"), PointSelection.regions(points))
    }

    @Test fun regionOnlyShowsMatchingPoints() {
        assertEquals(listOf("정자항", "슬도"),
            PointSelection.inRegion(points, "울산").map { it.name })
        assertEquals(emptyList<FishingPoint>(), PointSelection.inRegion(points, "경주"))
    }

    private fun point(id: String, region: String, name: String) = FishingPoint(
        id = id, region = region, name = name, latitude = 35.5, longitude = 129.3,
        types = emptyList(), fishNames = emptyList(), note = "", direction = ""
    )
}
