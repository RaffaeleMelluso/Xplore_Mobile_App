package com.example.progmob_progetto_melluso.map

import com.example.progmob_progetto_melluso.data.entities.PoiEntity

/**
 * Demo points of interest inserted on first launch.
 *
 * How it works: the POI table starts empty and the POI creation flow is not
 * implemented yet (checklist §5), so this list seeds the map with a few real
 * attractions around Turin so the map is populated and the proximity-based
 * visit detection can be exercised. Coordinates are approximate.
 */
object DemoPoiSeed {

    /** Attractions around Turin, ordered by id insertion order. */
    val turin: List<PoiEntity> = listOf(
        poi("Mole Antonelliana", 45.0689, 7.6935, "Monument", "Symbol of Turin, National Museum of Cinema inside"),
        poi("Piazza Castello", 45.0709, 7.6862, "Square", "Historic heart of the city"),
        poi("Palazzo Reale", 45.0713, 7.6861, "Museum", "Royal palace of the House of Savoy"),
        poi("Museo Egizio", 45.0685, 7.6842, "Museum", "One of the most important Egyptian museums in the world"),
        poi("Porta Palatina", 45.0752, 7.6853, "Monument", "Ancient Roman city gate"),
        poi("Duomo di Torino", 45.0736, 7.6855, "Church", "Cathedral of Saint John the Baptist"),
        poi("Piazza Vittorio Veneto", 45.0659, 7.6931, "Square", "One of the largest squares in Europe"),
        poi("Parco del Valentino", 45.0524, 7.6837, "Park", "Park on the Po river, hosts the Medieval Village"),
        poi("Borgo Medievale", 45.0519, 7.6814, "Monument", "Replica of a medieval village inside Valentino park"),
        poi("Basilica di Superga", 45.0810, 7.7681, "Church", "Baroque basilica on the Superga hill"),
        poi("Lingotto", 45.0330, 7.6655, "Building", "Former Fiat factory, now a complex with a rooftop track")
    )

    private fun poi(
        title: String,
        lat: Double,
        lon: Double,
        category: String,
        description: String
    ): PoiEntity = PoiEntity(
        lat = lat,
        lon = lon,
        title = title,
        description = description,
        category = category,
        createdByUserId = null,
        votes = 0
    )
}
