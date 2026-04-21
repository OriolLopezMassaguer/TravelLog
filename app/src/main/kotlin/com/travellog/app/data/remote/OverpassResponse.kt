package com.travellog.app.data.remote

import com.google.gson.annotations.SerializedName

data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList()
)

data class OverpassCenter(
    val lat: Double = 0.0,
    val lon: Double = 0.0
)

data class OverpassElement(
    val type: String = "",
    val id: Long = 0,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OverpassCenter? = null,
    val tags: Map<String, String> = emptyMap()
) {
    val name: String? get() = tags["name"]

    val latValue: Double get() = lat ?: center?.lat ?: 0.0
    val lonValue: Double get() = lon ?: center?.lon ?: 0.0

    val category: String get() = when {
        tags["amenity"] in FOOD_AMENITIES          -> "food"
        tags["amenity"] == "museum"
            || tags["tourism"] == "museum"         -> "museum"
        tags["tourism"] in listOf("attraction", "artwork", "gallery") -> "attraction"
        tags["tourism"] == "viewpoint"             -> "viewpoint"
        tags["tourism"] in ACCOMMODATION_TAGS
            || tags["amenity"] == "hotel"          -> "accommodation"
        tags["historic"] != null                   -> "historic"
        tags["leisure"] in listOf("park", "garden", "nature_reserve") -> "park"
        tags["amenity"] in listOf("theatre", "cinema") -> "entertainment"
        else                                       -> "other"
    }

    val address: String? get() {
        val parts = listOfNotNull(
            tags["addr:street"]?.let { street ->
                tags["addr:housenumber"]?.let { "$street $it" } ?: street
            },
            tags["addr:city"]
        )
        return parts.joinToString(", ").ifBlank { null }
    }

    companion object {
        private val FOOD_AMENITIES = listOf(
            "restaurant", "cafe", "bar", "pub", "fast_food",
            "food_court", "ice_cream", "biergarten"
        )
        private val ACCOMMODATION_TAGS = listOf(
            "hotel", "hostel", "guest_house", "motel", "apartment"
        )
    }
}
