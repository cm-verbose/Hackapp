package com.example.hackapp.ui.components.map

import com.example.hackapp.citypack.DistrictMapData
import com.example.hackapp.citypack.DistrictShape

/** Four hand-drawn districts for @Preview only; the real shapes come from assets/citypacks/<city>/districts.json. */
internal val PreviewDistrictMap = DistrictMapData(
    cityId = "preview",
    viewBox = listOf(0f, 0f, 400f, 300f),
    districts = listOf(
        DistrictShape("a", "A", null, listOf(100f, 90f), "M10,10 L190,10 L190,170 L10,170 Z"),
        DistrictShape("b", "B", null, listOf(290f, 90f), "M200,10 L390,10 L390,170 L200,170 Z"),
        DistrictShape("c", "C", null, listOf(100f, 230f), "M10,180 L190,180 L190,290 L10,290 Z"),
        DistrictShape("d", "D", "linked", listOf(290f, 230f), "M200,180 L390,180 L390,290 L200,290 Z"),
    ),
)
