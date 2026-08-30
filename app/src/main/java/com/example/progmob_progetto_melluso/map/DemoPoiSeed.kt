package com.example.progmob_progetto_melluso.map

import com.example.progmob_progetto_melluso.data.entities.PoiEntity

/**
 * Demo points of interest inserted when missing.
 *
 * How it works: the POI creation flow is community-driven (see
 * [com.example.progmob_progetto_melluso.poi.PoiRepository.addPoi]), but for the
 * demo we seed real attractions so the map, the ghost cards and the trends
 * page are populated from the first launch. Seeding is idempotent per title
 * (see [com.example.progmob_progetto_melluso.map.MapRepositoryImpl.ensurePoiSeed]):
 * every launch inserts only the entries that are not present yet, so new
 * cities can be added over time without duplicating existing POIs on
 * already-installed apps. Coordinates are approximate.
 *
 * Every POI carries a REAL photo bundled INSIDE the app (a drawable in
 * `res/drawable`, downloaded from Wikimedia Commons at seed time — no network
 * needed at runtime), shown in the map card and the trends ranking, and a
 * `city` so the trends page can agglomerate the votes of the POIs of each
 * city (city scope).
 */
object DemoPoiSeed {

    /** All the demo POIs across every seeded city ([AppContainer]/repositories use this). */
    val all: List<PoiEntity> get() = turin + cerignola + tokyo + milano + madrid + barcellona +
        paris + marseille + berlin + monaco

    /** Attractions around Turin, ordered by id insertion order. */
    val turin: List<PoiEntity> = listOf(
        poi("Mole Antonelliana", 45.0689, 7.6935, "Monument", "Symbol of Turin, National Museum of Cinema inside", "Torino", img("poi_mole_antonelliana")),
        poi("Piazza Castello", 45.0709, 7.6862, "Square", "Historic heart of the city", "Torino", img("poi_piazza_castello")),
        poi("Palazzo Reale", 45.0713, 7.6861, "Museum", "Royal palace of the House of Savoy", "Torino", img("poi_palazzo_reale")),
        poi("Museo Egizio", 45.0685, 7.6842, "Museum", "One of the most important Egyptian museums in the world", "Torino", img("poi_museo_egizio")),
        poi("Porta Palatina", 45.0752, 7.6853, "Monument", "Ancient Roman city gate", "Torino", img("poi_porta_palatina")),
        poi("Duomo di Torino", 45.0736, 7.6855, "Church", "Cathedral of Saint John the Baptist", "Torino", img("poi_duomo_di_torino")),
        poi("Piazza Vittorio Veneto", 45.0659, 7.6931, "Square", "One of the largest squares in Europe", "Torino", img("poi_piazza_vittorio_veneto")),
        poi("Parco del Valentino", 45.0524, 7.6837, "Park", "Park on the Po river, hosts the Medieval Village", "Torino", img("poi_parco_del_valentino")),
        poi("Borgo Medievale", 45.0519, 7.6814, "Monument", "Replica of a medieval village inside Valentino park", "Torino", null),
        poi("Basilica di Superga", 45.0810, 7.7681, "Church", "Baroque basilica on the Superga hill", "Torino", img("poi_basilica_di_superga")),
        poi("Lingotto", 45.0330, 7.6655, "Building", "Former Fiat factory, now a complex with a rooftop track", "Torino", img("poi_lingotto")),
        // Extra attractions referenced by the simulated travelers (ghosts), so
        // their visits/votes resolve to real POIs in the trends page.
        poi("Dipartimento di Informatica", 45.0622, 7.6629, "University", "Computer Science department of the University of Turin, Corso Svizzera", "Torino", null),
        poi("Parco Dora", 45.0905, 7.6745, "Park", "Park along the Dora river in northern Turin", "Torino", null),
        poi("Vitali Park", 45.0858, 7.7044, "Park", "Neighborhood park near Corso Mortara", "Torino", null),
        poi("Stazione Porta Nuova", 45.0612, 7.6782, "Station", "Main railway station of Turin", "Torino", img("poi_stazione_porta_nuova")),
        poi("Gran Madre di Dio", 45.0617, 7.7005, "Church", "Church on the Po river, opposite Piazza Vittorio", "Torino", img("poi_gran_madre_di_dio"))
    )

    /**
     * Attractions around Cerignola (Puglia), used for local testing.
     * Coordinates resolved through OSM Nominatim on 2026-08-24.
     */
    val cerignola: List<PoiEntity> = listOf(
        poi("Cattedrale San Pietro Apostolo", 41.2642, 15.8995, "Church", "Mother church of Cerignola (Duomo), in Piazza Duomo", "Cerignola", null),
        poi("Chiesa del Purgatorio", 41.2653, 15.8948, "Church", "Historic church on Via Don Giovanni Minzoni", "Cerignola", null),
        poi("Chiesa di San Francesco d'Assisi", 41.2665, 15.8928, "Church", "Historic Franciscan church of the old town", "Cerignola", null),
        poi("Piazza della Repubblica", 41.2656, 15.9040, "Square", "Main square of the city", "Cerignola", null),
        poi("Villa Comunale Umberto I", 41.2632, 15.9035, "Park", "Public garden in the city center", "Cerignola", null),
        poi("Museo Etnografico Cerignolano", 41.2646, 15.9032, "Museum", "Ethnographic museum of local traditions", "Cerignola", null),
        poi("Palazzo Cirillo-Farrusi", 41.2653, 15.8983, "Building", "Historic palace on Corso Garibaldi", "Cerignola", null),
        poi("Santuario Madonna di Ripalta", 41.2288, 15.8992, "Church", "Patron saint sanctuary south of the city", "Cerignola", null),
        poi("Stazione di Cerignola Città", 41.2639, 15.8920, "Station", "Railway station of Cerignola", "Cerignola", null)
    )

    /** Attractions in Tokyo (Japan). */
    val tokyo: List<PoiEntity> = listOf(
        poi("Senso-ji Temple", 35.7148, 139.7967, "Temple", "Ancient Buddhist temple in Asakusa, Tokyo's oldest", "Tokyo", img("poi_senso_ji_temple")),
        poi("Tokyo Skytree", 35.7101, 139.8107, "Tower", "Tallest tower in Japan with panoramic views", "Tokyo", img("poi_tokyo_skytree")),
        poi("Meiji Shrine", 35.6764, 139.6993, "Shrine", "Shinto shrine in a forest next to Harajuku", "Tokyo", img("poi_meiji_shrine")),
        poi("Shibuya Crossing", 35.6595, 139.7005, "Square", "Famous scramble crossing in front of Shibuya station", "Tokyo", img("poi_shibuya_crossing")),
        poi("Shinjuku Gyoen", 35.6852, 139.7100, "Park", "Large park mixing Japanese, French and English gardens", "Tokyo", img("poi_shinjuku_gyoen"))
    )

    /** Attractions in Milano (Italy). */
    val milano: List<PoiEntity> = listOf(
        poi("Duomo di Milano", 45.4642, 9.1900, "Church", "Gothic cathedral, third largest church in the world", "Milano", img("poi_duomo_di_milano")),
        poi("Galleria Vittorio Emanuele II", 45.4656, 9.1909, "Building", "Historic shopping arcade connecting piazza Duomo and La Scala", "Milano", img("poi_galleria_vittorio_emanuele_ii")),
        poi("Castello Sforzesco", 45.4703, 9.1795, "Castle", "15th-century castle with museums and art collections", "Milano", img("poi_castello_sforzesco")),
        poi("Parco Sempione", 45.4731, 9.1736, "Park", "Urban park behind the Castello Sforzesco", "Milano", img("poi_parco_sempione")),
        poi("Teatro alla Scala", 45.4670, 9.1894, "Theatre", "One of the most renowned opera houses in the world", "Milano", img("poi_teatro_alla_scala"))
    )

    /** Attractions in Madrid (Spain). */
    val madrid: List<PoiEntity> = listOf(
        poi("Palacio Real", 40.4180, -3.7140, "Palace", "Official residence of the Spanish royal family", "Madrid", img("poi_palacio_real")),
        poi("Parque del Retiro", 40.4153, -3.6840, "Park", "Large public park with a lake and the Crystal Palace", "Madrid", img("poi_parque_del_retiro")),
        poi("Puerta del Sol", 40.4168, -3.7038, "Square", "Heart of Madrid, kilometer zero of Spain", "Madrid", img("poi_puerta_del_sol")),
        poi("Museo del Prado", 40.4138, -3.6921, "Museum", "Main Spanish art museum, houses masterpieces of European painting", "Madrid", img("poi_museo_del_prado")),
        poi("Plaza Mayor", 40.4154, -3.7074, "Square", "Grand arcaded central square", "Madrid", img("poi_plaza_mayor"))
    )

    /** Attractions in Barcelona (Spain). */
    val barcellona: List<PoiEntity> = listOf(
        poi("Sagrada Família", 41.4036, 2.1744, "Church", "Gaudí's unfinished basilica, UNESCO heritage", "Barcellona", img("poi_sagrada_familia")),
        poi("Park Güell", 41.4145, 2.1527, "Park", "Gaudí's colorful public park on Carmel Hill", "Barcellona", img("poi_park_guell")),
        poi("Casa Batlló", 41.3916, 2.1649, "Building", "Gaudí's Modernist building on Passeig de Gràcia", "Barcellona", img("poi_casa_batllo")),
        poi("La Rambla", 41.3821, 2.1731, "Street", "Famous tree-lined pedestrian street", "Barcellona", img("poi_la_rambla")),
        poi("Barceloneta Beach", 41.3755, 2.1915, "Beach", "Popular city beach next to the old fishing quarter", "Barcellona", img("poi_barceloneta_beach"))
    )

    /** Attractions in Paris (France). */
    val paris: List<PoiEntity> = listOf(
        poi("Tour Eiffel", 48.8584, 2.2945, "Monument", "Iron lattice tower, symbol of Paris on the Champ de Mars", "Paris", img("poi_tour_eiffel")),
        poi("Musée du Louvre", 48.8606, 2.3376, "Museum", "World's largest art museum with the Glass Pyramid", "Paris", img("poi_musee_du_louvre")),
        poi("Notre-Dame de Paris", 48.8530, 2.3499, "Church", "Gothic cathedral on the Île de la Cité", "Paris", img("poi_notre_dame_de_paris")),
        poi("Arc de Triomphe", 48.8738, 2.2950, "Monument", "Triumphal arch at the west end of the Champs-Élysées", "Paris", img("poi_arc_de_triomphe")),
        poi("Montmartre / Sacré-Cœur", 48.8867, 2.3431, "Church", "Hilltop basilica with the best view of the city", "Paris", img("poi_montmartre_sacre_cur"))
    )

    /** Attractions in Marseille (France). */
    val marseille: List<PoiEntity> = listOf(
        poi("Vieux-Port", 43.2951, 5.3740, "Harbour", "Historic old port, entry point to the city", "Marsiglia", img("poi_vieux_port")),
        poi("Basilique Notre-Dame de la Garde", 43.2840, 5.3710, "Church", "Neo-Byzantine basilica guarding the city from a hill", "Marsiglia", img("poi_basilique_notre_dame_de_la_garde")),
        poi("Calanques National Park", 43.2110, 5.4340, "Park", "Stunning limestone fjords between Marseille and Cassis", "Marsiglia", img("poi_calanques_national_park")),
        poi("MUCEM", 43.2969, 5.3619, "Museum", "Museum of European and Mediterranean Civilisations on the harbour", "Marsiglia", img("poi_mucem"))
    )

    /** Attractions in Berlin (Germany). */
    val berlin: List<PoiEntity> = listOf(
        poi("Brandenburg Gate", 52.5163, 13.3777, "Monument", "Neoclassical gate and symbol of the reunified city", "Berlino", img("poi_brandenburg_gate")),
        poi("Reichstag", 52.5186, 13.3762, "Building", "Parliament building with a glass dome", "Berlino", img("poi_reichstag")),
        poi("Berliner Dom", 52.5190, 13.4010, "Church", "Historic Protestant cathedral on Museum Island", "Berlino", img("poi_berliner_dom")),
        poi("East Side Gallery", 52.5047, 13.4397, "Monument", "Longest remaining stretch of the Berlin Wall covered in murals", "Berlino", img("poi_east_side_gallery")),
        poi("Tiergarten", 52.5113, 13.3620, "Park", "Huge central park around the Siegessäule", "Berlino", img("poi_tiergarten"))
    )

    /** Attractions in Munich (Germany). */
    val monaco: List<PoiEntity> = listOf(
        poi("Marienplatz", 48.1374, 11.5755, "Square", "Central square with the New Town Hall's Glockenspiel", "Monaco", img("poi_marienplatz")),
        poi("Neues Rathaus", 48.1375, 11.5754, "Building", "Neo-gothic New Town Hall with the famous carillon", "Monaco", img("poi_neues_rathaus")),
        poi("Englischer Garten", 48.1622, 11.6010, "Park", "One of the largest urban parks in the world", "Monaco", img("poi_englischer_garten")),
        poi("Nymphenburg Palace", 48.1583, 11.5033, "Palace", "Baroque palace on the western edge of the city", "Monaco", img("poi_nymphenburg_palace")),
        poi("Alte Pinakothek", 48.1483, 11.5700, "Museum", "Famous art museum with European masters", "Monaco", img("poi_alte_pinakothek"))
    )

    /**
     * Name of the bundled drawable holding the real photo of the place (for
     * example `poi_mole_antonelliana`, file `res/drawable/poi_mole_antonelliana.jpg`).
     * The images live inside the APK, so they always load, offline included.
     */
    private fun img(name: String): String = name

    private fun poi(
        title: String,
        lat: Double,
        lon: Double,
        category: String,
        description: String,
        city: String,
        imageRes: String?
    ): PoiEntity = PoiEntity(
        lat = lat,
        lon = lon,
        title = title,
        description = description,
        category = category,
        createdByUserId = null,
        city = city,
        imageResName = imageRes,
        votes = 0
    )
}
