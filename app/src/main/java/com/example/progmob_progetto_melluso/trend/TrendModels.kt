package com.example.progmob_progetto_melluso.trend

/**
 * One ranked attraction (POI) of the trends page, with the votes it received
 * inside the selected period.
 *
 * @param poiId the POI id, used to vote it from the ranking.
 * @param title the attraction name shown in the row.
 * @param category the POI category (museum, park, ...), shown as subtitle.
 * @param city the city the POI belongs to, shown as subtitle.
 * @param description optional POI note, shown in the row subtitle.
 * @param imageResName name of the bundled drawable with the real photo of the
 *   place (demo POIs), shown as the row thumbnail; null when the POI carries
 *   a user photo instead (or no photo at all).
 * @param imageUri local URI of the photo the user attached to their POI.
 * @param windowScore sum of the votes received inside the period.
 * @param windowVotes number of votes received inside the period.
 * @param myVote the signed-in user's vote on this POI (1, -1 or 0), so the
 *   ranking shows the same up/down state as the journal and the map.
 */
data class PoiTrendItem(
    val poiId: Long,
    val title: String,
    val category: String?,
    val city: String?,
    val description: String?,
    val imageResName: String? = null,
    val imageUri: String? = null,
    val windowScore: Long,
    val windowVotes: Long,
    val myVote: Int
)

/**
 * One ranked city of the trends page: the sum of the votes received by the
 * POIs of that city inside the selected period.
 */
data class CityTrendItem(
    val cityName: String,
    val windowScore: Long,
    val windowVotes: Long
)

/**
 * All-time stats of a single POI, shown in the attraction detail screen: the
 * score summed over ALL votes ever cast (not just the period window).
 */
data class PoiStats(
    val totalScore: Long,
    val totalVotes: Int
)
