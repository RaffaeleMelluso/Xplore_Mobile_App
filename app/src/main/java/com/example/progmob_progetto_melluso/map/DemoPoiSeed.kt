package com.example.progmob_progetto_melluso.map

import com.example.progmob_progetto_melluso.data.entities.PoiEntity

/**
 * Demo points of interest inserted when missing.
 *
 * How it works: the POI creation flow is not implemented yet (checklist §5),
 * so this object seeds the map with real attractions around Turin and
 * Cerignola so the map is populated and the proximity-based visit detection
 * can be exercised. Seeding is idempotent per title (see
 * [com.example.progmob_progetto_melluso.map.MapRepositoryImpl.ensurePoiSeed]):
 * every launch inserts only the entries that are not present yet, so new
 * cities can be added over time without duplicating existing POIs on
 * already-installed apps. Coordinates are approximate.
 */
object DemoPoiSeed {

    /** Attractions around Turin, ordered by id insertion order. */
    val turin: List<PoiEntity> = listOf(
        poi("Mole Antonelliana", 45.0689, 7.6935, "Monument", "Symbol of Turin, National Museum of Cinema inside", "Torino"),
        poi("Piazza Castello", 45.0709, 7.6862, "Square", "Historic heart of the city", "Torino"),
        poi("Palazzo Reale", 45.0713, 7.6861, "Museum", "Royal palace of the House of Savoy", "Torino"),
        poi("Museo Egizio", 45.0685, 7.6842, "Museum", "One of the most important Egyptian museums in the world", "Torino"),
        poi("Porta Palatina", 45.0752, 7.6853, "Monument", "Ancient Roman city gate", "Torino"),
        poi("Duomo di Torino", 45.0736, 7.6855, "Church", "Cathedral of Saint John the Baptist", "Torino"),
        poi("Piazza Vittorio Veneto", 45.0659, 7.6931, "Square", "One of the largest squares in Europe", "Torino"),
        poi("Parco del Valentino", 45.0524, 7.6837, "Park", "Park on the Po river, hosts the Medieval Village", "Torino"),
        poi("Borgo Medievale", 45.0519, 7.6814, "Monument", "Replica of a medieval village inside Valentino park", "Torino"),
        poi("Basilica di Superga", 45.0810, 7.7681, "Church", "Baroque basilica on the Superga hill", "Torino"),
        poi("Lingotto", 45.0330, 7.6655, "Building", "Former Fiat factory, now a complex with a rooftop track", "Torino"),
        // Extra attractions referenced by the simulated travelers (ghosts), so
        // their visits/votes resolve to real POIs in the trends page.
        poi("Dipartimento di Informatica", 45.0622, 7.6629, "University", "Computer Science department of the University of Turin, Corso Svizzera", "Torino"),
        poi("Parco Dora", 45.0905, 7.6745, "Park", "Park along the Dora river in northern Turin", "Torino"),
        poi("Vitali Park", 45.0858, 7.7044, "Park", "Neighborhood park near Corso Mortara", "Torino"),
        poi("Stazione Porta Nuova", 45.0612, 7.6782, "Station", "Main railway station of Turin", "Torino"),
        poi("Gran Madre di Dio", 45.0617, 7.7005, "Church", "Church on the Po river, opposite Piazza Vittorio", "Torino")
    )

    /**
     * Attractions around Cerignola (Puglia), used for local testing.
     * Coordinates resolved through OSM Nominatim on 2026-08-24.
     */
    val cerignola: List<PoiEntity> = listOf(
        poi("Cattedrale San Pietro Apostolo", 41.2642, 15.8995, "Church", "Mother church of Cerignola (Duomo), in Piazza Duomo", "Cerignola"),
        poi("Chiesa del Purgatorio", 41.2653, 15.8948, "Church", "Historic church on Via Don Giovanni Minzoni", "Cerignola"),
        poi("Chiesa di San Francesco d'Assisi", 41.2665, 15.8928, "Church", "Historic Franciscan church of the old town", "Cerignola"),
        poi("Piazza della Repubblica", 41.2656, 15.9040, "Square", "Main square of the city", "Cerignola"),
        poi("Villa Comunale Umberto I", 41.2632, 15.9035, "Park", "Public garden in the city center", "Cerignola"),
        poi("Museo Etnografico Cerignolano", 41.2646, 15.9032, "Museum", "Ethnographic museum of local traditions", "Cerignola"),
        poi("Palazzo Cirillo-Farrusi", 41.2653, 15.8983, "Building", "Historic palace on Corso Garibaldi", "Cerignola"),
        poi("Santuario Madonna di Ripalta", 41.2288, 15.8992, "Church", "Patron saint sanctuary south of the city", "Cerignola"),
        poi("Stazione di Cerignola Città", 41.2639, 15.8920, "Station", "Railway station of Cerignola", "Cerignola")
    )

    private fun poi(
        title: String,
        lat: Double,
        lon: Double,
        category: String,
        description: String,
        city: String
    ): PoiEntity = PoiEntity(
        lat = lat,
        lon = lon,
        title = title,
        description = description,
        category = category,
        createdByUserId = null,
        city = city,
        votes = 0
    )
}
