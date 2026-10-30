package com.eastsea.badahannun.domain

import com.eastsea.badahannun.data.FishingPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class PointSelectionTest {
    private val points = listOf(
        point("ulsan-jeongja", "울산", "정자항"),
        point("pohang-guryongpo", "포항", "구룡포"),
        point("ulsan-dangsa", "울산", "당사"),
        point("busan-gijang", "부산", "기장"),
        point("ulsan-jujeon", "울산", "주전"),
        point("gyeongju-gampo", "경주", "감포"),
        point("ulsan-seuldo", "울산", "슬도"),
        point("ulsan-jinha", "울산", "진하")
    )

    @Test fun preferredRegionsAreFirstEvenWhenCatalogIsUnordered() {
        assertEquals(listOf("울산", "경주", "포항", "부산"),
            PointSelection.regions(points))
    }

    @Test fun preferredUlsanPlacesAreSlidoJujeonDangsaThenRemainder() {
        assertEquals(listOf("ulsan-seuldo", "ulsan-jujeon", "ulsan-dangsa",
            "ulsan-jeongja", "ulsan-jinha"),
            PointSelection.inRegion(points, "울산").map { it.id })
    }

    @Test fun regionsOnlyContainPresentValuesAndOthersKeepCatalogOrder() {
        val extended = points + point("etc-one", "기타", "A")
        assertEquals(listOf("울산", "경주", "포항", "부산", "기타"),
            PointSelection.regions(extended))
        assertEquals(listOf("pohang-guryongpo"),
            PointSelection.inRegion(points, "포항").map { it.id })
        assertEquals(emptyList<FishingPoint>(), PointSelection.inRegion(points, "대구"))
    }

    private fun point(id: String, region: String, name: String) = FishingPoint(
        id = id, region = region, name = name, latitude = 35.5, longitude = 129.3,
        types = emptyList(), fishNames = emptyList(), note = "", direction = ""
    )
}
