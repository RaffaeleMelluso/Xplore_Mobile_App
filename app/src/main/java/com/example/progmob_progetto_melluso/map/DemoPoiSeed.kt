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
 * Every POI carries a REAL photo (a Wikimedia Commons file, verified to exist
 * via the Commons API) shown with Coil in the map card and the trends ranking,
 * and a `city` so the trends page can agglomerate the votes of the POIs of
 * each city (city scope).
 */
object DemoPoiSeed {

    /** All the demo POIs across every seeded city ([AppContainer]/repositories use this). */
    val all: List<PoiEntity> get() = turin + cerignola + tokyo + milano + madrid + barcellona +
        paris + marseille + berlin + monaco

    /** Attractions around Turin, ordered by id insertion order. */
    val turin: List<PoiEntity> = listOf(
        poi("Mole Antonelliana", 45.0689, 7.6935, "Monument", "Symbol of Turin, National Museum of Cinema inside", "Torino", file("Mole_Antonelliana.JPG")),
        poi("Piazza Castello", 45.0709, 7.6862, "Square", "Historic heart of the city", "Torino", file("Torino - Piazza Castello (24269304747).jpg")),
        poi("Palazzo Reale", 45.0713, 7.6861, "Museum", "Royal palace of the House of Savoy", "Torino", file("Torino - Palazzo Reale 1628.jpg")),
        poi("Museo Egizio", 45.0685, 7.6842, "Museum", "One of the most important Egyptian museums in the world", "Torino", file("Museo Egizio e Galleria sabauda, Torino.jpg")),
        poi("Porta Palatina", 45.0752, 7.6853, "Monument", "Ancient Roman city gate", "Torino", file("PortaPalatina (Turin).jpg")),
        poi("Duomo di Torino", 45.0736, 7.6855, "Church", "Cathedral of Saint John the Baptist", "Torino", file("Torino - Duomo 0538.jpg")),
        poi("Piazza Vittorio Veneto", 45.0659, 7.6931, "Square", "One of the largest squares in Europe", "Torino", file("Piazza Vittorio Veneto Torino 2019.jpg")),
        poi("Parco del Valentino", 45.0524, 7.6837, "Park", "Park on the Po river, hosts the Medieval Village", "Torino", file("Torino - Parco del Valentino - 2.jpg")),
        poi("Borgo Medievale", 45.0519, 7.6814, "Monument", "Replica of a medieval village inside Valentino park", "Torino", null),
        poi("Basilica di Superga", 45.0810, 7.7681, "Church", "Baroque basilica on the Superga hill", "Torino", file("Basilica di Superga (Turin).jpg")),
        poi("Lingotto", 45.0330, 7.6655, "Building", "Former Fiat factory, now a complex with a rooftop track", "Torino", file("Lingotto.JPG")),
        // Extra attractions referenced by the simulated travelers (ghosts), so
        // their visits/votes resolve to real POIs in the trends page.
        poi("Dipartimento di Informatica", 45.0622, 7.6629, "University", "Computer Science department of the University of Turin, Corso Svizzera", "Torino", null),
        poi("Parco Dora", 45.0905, 7.6745, "Park", "Park along the Dora river in northern Turin", "Torino", null),
        poi("Vitali Park", 45.0858, 7.7044, "Park", "Neighborhood park near Corso Mortara", "Torino", null),
        poi("Stazione Porta Nuova", 45.0612, 7.6782, "Station", "Main railway station of Turin", "Torino", file("10-11-28 Porta Nuova Interior.JPG")),
        poi("Gran Madre di Dio", 45.0617, 7.7005, "Church", "Church on the Po river, opposite Piazza Vittorio", "Torino", file("Chiesa della Gran Madre di Dio (Torino).JPG"))
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
        poi("Senso-ji Temple", 35.7148, 139.7967, "Temple", "Ancient Buddhist temple in Asakusa, Tokyo's oldest", "Tokyo", file("Senso-Ji Temple - 09.2018 - Clconci.jpg")),
        poi("Tokyo Skytree", 35.7101, 139.8107, "Tower", "Tallest tower in Japan with panoramic views", "Tokyo", file("20181123 Tokyo Skytree-2.jpg")),
        poi("Meiji Shrine", 35.6764, 139.6993, "Shrine", "Shinto shrine in a forest next to Harajuku", "Tokyo", file("Meiji-jingu-pathway.jpg")),
        poi("Shibuya Crossing", 35.6595, 139.7005, "Square", "Famous scramble crossing in front of Shibuya station", "Tokyo", file("Tokyo Shibuya Scramble Crossing 2018-10-09.jpg")),
        poi("Shinjuku Gyoen", 35.6852, 139.7100, "Park", "Large park mixing Japanese, French and English gardens", "Tokyo", file("Shinjuku Gyoen National Garden, Tokyo, 20240822 1447 5457.jpg"))
    )

    /** Attractions in Milano (Italy). */
    val milano: List<PoiEntity> = listOf(
        poi("Duomo di Milano", 45.4642, 9.1900, "Church", "Gothic cathedral, third largest church in the world", "Milano", file("20110724 Milan Cathedral 5260.jpg")),
        poi("Galleria Vittorio Emanuele II", 45.4656, 9.1909, "Building", "Historic shopping arcade connecting piazza Duomo and La Scala", "Milano", file("Galleria Vittorio Emanuele II 2382.jpg")),
        poi("Castello Sforzesco", 45.4703, 9.1795, "Castle", "15th-century castle with museums and art collections", "Milano", file("Castello Sforzesco (Milan) - main entrance.jpg")),
        poi("Parco Sempione", 45.4731, 9.1736, "Park", "Urban park behind the Castello Sforzesco", "Milano", file("Parco Sempione - Milano, 2023.jpg")),
        poi("Teatro alla Scala", 45.4670, 9.1894, "Theatre", "One of the most renowned opera houses in the world", "Milano", file("20110725 Milano La Scala 5507.jpg"))
    )

    /** Attractions in Madrid (Spain). */
    val madrid: List<PoiEntity> = listOf(
        poi("Palacio Real", 40.4180, -3.7140, "Palace", "Official residence of the Spanish royal family", "Madrid", file("Palacio Real de Madrid - 13.jpg")),
        poi("Parque del Retiro", 40.4153, -3.6840, "Park", "Large public park with a lake and the Crystal Palace", "Madrid", file("Estanque del Parque del Retiro - 01.jpg")),
        poi("Puerta del Sol", 40.4168, -3.7038, "Square", "Heart of Madrid, kilometer zero of Spain", "Madrid", file("Puerta del Sol - 02.jpg")),
        poi("Museo del Prado", 40.4138, -3.6921, "Museum", "Main Spanish art museum, houses masterpieces of European painting", "Madrid", file("Museo del Prado (Madrid) 09.jpg")),
        poi("Plaza Mayor", 40.4154, -3.7074, "Square", "Grand arcaded central square", "Madrid", file("Plaza Mayor de Madrid - 01.jpg"))
    )

    /** Attractions in Barcelona (Spain). */
    val barcellona: List<PoiEntity> = listOf(
        poi("Sagrada Família", 41.4036, 2.1744, "Church", "Gaudí's unfinished basilica, UNESCO heritage", "Barcellona", file("Sagrada Familia March 2015-10a.jpg")),
        poi("Park Güell", 41.4145, 2.1527, "Park", "Gaudí's colorful public park on Carmel Hill", "Barcellona", file("Park Güell 02.jpg")),
        poi("Casa Batlló", 41.3916, 2.1649, "Building", "Gaudí's Modernist building on Passeig de Gràcia", "Barcellona", file("Casa Batlló 01.jpg")),
        poi("La Rambla", 41.3821, 2.1731, "Street", "Famous tree-lined pedestrian street", "Barcellona", file("15-10-27-Vista des de l'estàtua de Colom a Barcelona-WMA 2791.jpg")),
        poi("Barceloneta Beach", 41.3755, 2.1915, "Beach", "Popular city beach next to the old fishing quarter", "Barcellona", file("Beach, Barcelona (P1170713).jpg"))
    )

    /** Attractions in Paris (France). */
    val paris: List<PoiEntity> = listOf(
        poi("Tour Eiffel", 48.8584, 2.2945, "Monument", "Iron lattice tower, symbol of Paris on the Champ de Mars", "Paris", file("Tour Eiffel Wikimedia Commons.jpg")),
        poi("Musée du Louvre", 48.8606, 2.3376, "Museum", "World's largest art museum with the Glass Pyramid", "Paris", file("Louvre Museum Wikimedia Commons.jpg")),
        poi("Notre-Dame de Paris", 48.8530, 2.3499, "Church", "Gothic cathedral on the Île de la Cité", "Paris", file("Paris, Notre Dame -- 2014 -- 1458-65.jpg")),
        poi("Arc de Triomphe", 48.8738, 2.2950, "Monument", "Triumphal arch at the west end of the Champs-Élysées", "Paris", file("Arc Triomphe.jpg")),
        poi("Montmartre / Sacré-Cœur", 48.8867, 2.3431, "Church", "Hilltop basilica with the best view of the city", "Paris", file("Basilique du Sacré-Cœur de Montmartre - Paris - GT-01 - 2024.jpg"))
    )

    /** Attractions in Marseille (France). */
    val marseille: List<PoiEntity> = listOf(
        poi("Vieux-Port", 43.2951, 5.3740, "Harbour", "Historic old port, entry point to the city", "Marsiglia", file("Marseille Vieux Port Night.jpg")),
        poi("Basilique Notre-Dame de la Garde", 43.2840, 5.3710, "Church", "Neo-Byzantine basilica guarding the city from a hill", "Marsiglia", file("Notre-Dame de la Garde Marseille 2024.jpg")),
        poi("Calanques National Park", 43.2110, 5.4340, "Park", "Stunning limestone fjords between Marseille and Cassis", "Marsiglia", file("En-Vau calanque 1.jpg")),
        poi("MUCEM", 43.2969, 5.3619, "Museum", "Museum of European and Mediterranean Civilisations on the harbour", "Marsiglia", file("Mucem et Cathédrale Sainte-Marie-Majeure de Marseille.jpg"))
    )

    /** Attractions in Berlin (Germany). */
    val berlin: List<PoiEntity> = listOf(
        poi("Brandenburg Gate", 52.5163, 13.3777, "Monument", "Neoclassical gate and symbol of the reunified city", "Berlino", file("Brandenburger_Tor_abends.jpg")),
        poi("Reichstag", 52.5186, 13.3762, "Building", "Parliament building with a glass dome", "Berlino", file("Reichstag pano.jpg")),
        poi("Berliner Dom", 52.5190, 13.4010, "Church", "Historic Protestant cathedral on Museum Island", "Berlino", file("Berliner Dom, Ostseite, 160402, ako.jpg")),
        poi("East Side Gallery", 52.5047, 13.4397, "Monument", "Longest remaining stretch of the Berlin Wall covered in murals", "Berlino", file("Berlin Wall near East Side Gallery.JPG")),
        poi("Tiergarten", 52.5113, 13.3620, "Park", "Huge central park around the Siegessäule", "Berlino", file("Grosser Tiergarten 2015.jpg"))
    )

    /** Attractions in Munich (Germany). */
    val monaco: List<PoiEntity> = listOf(
        poi("Marienplatz", 48.1374, 11.5755, "Square", "Central square with the New Town Hall's Glockenspiel", "Monaco", file("Marienplatz Munich Germany 1970s.jpg")),
        poi("Neues Rathaus", 48.1375, 11.5754, "Building", "Neo-gothic New Town Hall with the famous carillon", "Monaco", file("Neues Rathaus Munich March 2013.JPG")),
        poi("Englischer Garten", 48.1622, 11.6010, "Park", "One of the largest urban parks in the world", "Monaco", file("Monopteros, Englischer Garten, Munich, 20251110 0846 6048.jpg")),
        poi("Nymphenburg Palace", 48.1583, 11.5033, "Palace", "Baroque palace on the western edge of the city", "Monaco", file("Image-Schloss Nymphenburg Munich CC.jpg")),
        poi("Alte Pinakothek", 48.1483, 11.5700, "Museum", "Famous art museum with European masters", "Monaco", file("Alte Pinakothek lateral.jpg"))
    )

    /**
     * Builds the Wikimedia Commons URL of a real photo of the place.
     *
     * How it works: `Special:FilePath` resolves the file title to the actual
     * upload URL; `?width=800` asks Commons to serve a downscaled version
     * (the app shows thumbnails, so the full-size original would waste data).
     * The filenames below were verified to exist through the Commons API.
     */
    private fun file(name: String): String =
        "https://commons.wikimedia.org/wiki/Special:FilePath/${name.replace(' ', '_')}?width=800"

    private fun poi(
        title: String,
        lat: Double,
        lon: Double,
        category: String,
        description: String,
        city: String,
        imageUrl: String?
    ): PoiEntity = PoiEntity(
        lat = lat,
        lon = lon,
        title = title,
        description = description,
        category = category,
        createdByUserId = null,
        city = city,
        imageUrl = imageUrl,
        votes = 0
    )
}
