package com.example.progmob_progetto_melluso.ghost

import com.example.progmob_progetto_melluso.data.entities.GhostEntity
import com.example.progmob_progetto_melluso.data.entities.PoiEntity
import com.example.progmob_progetto_melluso.journal.JournalMedia
import com.example.progmob_progetto_melluso.journal.JournalTransport
import com.example.progmob_progetto_melluso.journal.JournalVisit
import com.example.progmob_progetto_melluso.travel.TravelConfig
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Simulates other travelers ("ghosts") moving along realistic routes in the
 * two demo cities (Turin around the Informatics faculty / Parco Dora, and
 * Cerignola city centre) using deterministic waypoints on real streets.
 *
 * How it works: there is no backend yet (the local ghost simulation requirement), so this class holds a
 * fixed set of ghost personas. Each persona has an ordered route of real
 * waypoints, a speed, an online/past flag and the journal-like detail shown
 * when tapped. For a given anchor (user position) we pick the closest demo
 * zone (Torino or Cerignola) and return that zone's ghosts. An online ghost's
 * current position is computed by advancing a fixed distance along its route
 * from a fixed epoch — given the same inputs the output is identical, so the
 * simulator stays deterministic and unit-testable. When a real backend exists,
 * only the ghost data source changes; the rendering pipeline stays.
 *
 * Visibility is bounded (the map visibility limits): only the ghosts whose marker is within
 * [config.ghostVisibilityRadiusMeters] from the user are returned, and if more
 * than [config.maxGhostsShown] are in range the closest ones win. The cost of
 * rendering is therefore constant no matter how many users actually exist.
 */
class GhostFactory(
    private val config: TravelConfig = TravelConfig(),
    private val clock: () -> Long = System::currentTimeMillis
) {

    /**
     * Computes the current ghosts for the zone nearest to the given center,
     * filtered by visibility radius and capped to [TravelConfig.maxGhostsShown].
     *
     * @param centerLat latitude of the center (user position) in degrees.
     * @param centerLon longitude of the center (user position) in degrees.
     * @param nowMillis current time in UTC millis, used as the animation clock
     *   for the online ghosts (ignored for past visitors).
     * @return the ghosts within the visibility radius, closest first, no more
     *   than [TravelConfig.maxGhostsShown]. Their `userId` is stable so the UI
     *   can update markers in place instead of recreating them.
     */
    fun tick(
        centerLat: Double,
        centerLon: Double,
        nowMillis: Long,
        poisByTitle: Map<String, PoiEntity> = emptyMap()
    ): List<Ghost> {
        val zone = TURIN.distanceMetersTo(centerLat, centerLon) <=
            CERIGNOLA.distanceMetersTo(centerLat, centerLon)
        val personas = if (zone) TURIN.personas else CERIGNOLA.personas
        val ghosts = personas.map { persona ->
            persona.toGhost(nowMillis, clock, poisByTitle)
        }
        // Radius filter: keep only the ghosts actually near the user, so the
        // stream never carries the whole planet of users down to the UI.
        return ghosts
            .filter { ghost ->
                GeoHaversine.meters(centerLat, centerLon, ghost.marker.lat, ghost.marker.lon) <=
                    config.ghostVisibilityRadiusMeters
            }
            // Cap: if more are in range, show the closest ones so the marker
            // count on the map is bounded regardless of total users.
            .sortedBy { ghost ->
                GeoHaversine.meters(centerLat, centerLon, ghost.marker.lat, ghost.marker.lon)
            }
            .take(config.maxGhostsShown.coerceAtLeast(1))
    }

    // ---------------------------------------------------------------------
    // Demo content
    // ---------------------------------------------------------------------

    private data class Zone(
        val personas: List<Persona>
    ) {
        fun distanceMetersTo(lat: Double, lon: Double): Double =
            personas.minOf { it.route.distanceMetersTo(lat, lon) }
    }

    private data class Persona(
        val userId: String,
        val name: String,
        val description: String,
        val city: String,
        val isOnlineNow: Boolean,
        val route: Route,
        val speedMetersPerSecond: Double,
        val mediaTitles: List<Pair<String, String>>, // (mediaType, label)
        // The places this traveler visited (with their vote), from the shared
        // [DemoGhostVisits] source: the same data the trends seed stores as
        // real votes, so the ghost card and the trends always agree.
        val visits: List<DemoGhostVisits.Visit>,
        // The transport segments this traveler reported (examples of the
        // journal transport flow, same model as a real trip).
        val transports: List<DemoGhostVisits.TransportSpec> = emptyList()
    ) {
        fun toGhost(nowMillis: Long, clock: () -> Long, poisByTitle: Map<String, PoiEntity>): Ghost {
            // Past visitors sit at a fixed position on their route (an offset
            // derived from their id), at a timestamp in the past. Online ghosts
            // advance along the route from a fixed epoch.
            val online = isOnlineNow
            val markerTime = if (online) nowMillis else PAST_VISIT_EPOCH + (userId.hashCode().toLong() and 0x7fffffffL)
            val distanceAlong = if (online) {
                ((nowMillis - ONLINE_EPOCH_MILLIS).coerceAtLeast(0L) * speedMetersPerSecond) / 1000.0
            } else {
                // A deterministic waypoint index for the past visitor, so it
                // does not drift: it is parked on a real, stable position.
                (markerTime / 1_000L % route.waypoints.size.toLong()).toInt().toDouble() * route.segmentMeters
            }
            val position = route.positionAt(distanceAlong)
            return Ghost(
                marker = GhostEntity(
                    userId = userId,
                    lat = position.first,
                    lon = position.second,
                    timestamp = markerTime
                ),
                path = route.waypoints,
                name = name,
                description = description,
                isOnlineNow = online,
                city = city,
                media = buildMedia(userId),
                // The visits reference the REAL POIs (id + live community
                // score from the votes), so the card shows exactly what the
                // trends page aggregates. Unknown titles (pre-seed) fall back
                // to score 0 and a synthetic id, and self-correct on the next
                // emission once the POIs/votes are seeded.
                visits = visits.map { spec ->
                    val poi = poisByTitle[spec.poiTitle]
                    JournalVisit(
                        id = (poi?.id ?: -1L) * -1L,
                        poiId = poi?.id ?: -1L,
                        poiTitle = spec.poiTitle,
                        visitedAtUtcMillis = nowMillis - spec.minutesAgo * 60_000L,
                        note = null,
                        score = poi?.votes ?: 0,
                        myVote = spec.myVote
                    )
                },
                transports = transports.mapIndexed { index, spec ->
                    JournalTransport(
                        id = userId.hashCode().toLong() * -1L - (index + 1),
                        travelId = -1L,
                        mode = spec.mode,
                        lineName = spec.lineName,
                        startUtcMillis = nowMillis - spec.minutesAgo * 60_000L
                    )
                }
            )
        }

        private fun buildMedia(userId: String): List<JournalMedia> =
            mediaTitles.mapIndexed { index, (type, label) ->
                JournalMedia(
                    // Negative ids: markers don't collide with real media ids.
                    id = userId.hashCode().toLong() * -1L - (index + 1),
                    mediaType = type,
                    imageUri = "simulated://$label",
                    caption = label,
                    capturedAtUtcMillis = ONLINE_EPOCH_MILLIS + index * 3_600_000L
                )
            }
    }

    /** A polygon line of real waypoints (lat, lon), with precomputed lengths. */
    private class Route(
        val waypoints: List<Pair<Double, Double>>
    ) {
        private val cumulative: DoubleArray
        val totalMeters: Double
        val segmentMeters: Double

        init {
            cumulative = DoubleArray(waypoints.size)
            var acc = 0.0
            for (i in 1 until waypoints.size) {
                val (lat1, lon1) = waypoints[i - 1]
                val (lat2, lon2) = waypoints[i]
                acc += GeoHaversine.meters(lat1, lon1, lat2, lon2)
                cumulative[i] = acc
            }
            totalMeters = acc
            segmentMeters = totalMeters / (waypoints.size - 1).coerceAtLeast(1)
        }

        /** Returns the (lat, lon) after travelling [distanceMeters] along the route. */
        fun positionAt(distanceMeters: Double): Pair<Double, Double> {
            if (waypoints.isEmpty()) return 0.0 to 0.0
            if (waypoints.size == 1) return waypoints[0]
            val d = ((distanceMeters % totalMeters) + totalMeters) % totalMeters
            for (i in 1 until waypoints.size) {
                if (d <= cumulative[i]) {
                    val segStart = cumulative[i - 1]
                    val segLen = cumulative[i] - segStart
                    val ratio = if (segLen == 0.0) 0.0 else (d - segStart) / segLen
                    return lerp(waypoints[i - 1], waypoints[i], ratio)
                }
            }
            return waypoints.last()
        }

        fun distanceMetersTo(lat: Double, lon: Double): Double =
            waypoints.minOf { GeoHaversine.meters(lat, lon, it.first, it.second) }

        private fun lerp(a: Pair<Double, Double>, b: Pair<Double, Double>, ratio: Double): Pair<Double, Double> =
            (a.first + (b.first - a.first) * ratio) to (a.second + (b.second - a.second) * ratio)
    }

    private object GeoHaversine {
        private const val R = 6_371_000.0
        fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return R * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }

    // ---------------------------------------------------------------------
    // Personas
    // ---------------------------------------------------------------------

    companion object {
        /** Fixed zero-base for the online ghosts' motion (same inputs → same output). */
        private const val ONLINE_EPOCH_MILLIS = 1_777_363_200_000L // 2026-04-30T00:00:00Z
        /** Fixed reference for past visitors. */
        private const val PAST_VISIT_EPOCH = 1_770_000_000_000L

        // ---------------------------------------------------------------
        // Demo routes: real street waypoints (fetched once from OSRM foot
        // routing, see GhostRoutes.kt) so the polylines follow actual roads
        // instead of straight lines through buildings.
        // ---------------------------------------------------------------
        private val TURIN_ROUTE_A = Route(GhostRoutes.TURIN_ROUTE_A)
        private val TURIN_ROUTE_B = Route(GhostRoutes.TURIN_ROUTE_B)
        private val TURIN_PARCO_DORA_C = Route(GhostRoutes.TURIN_PARCO_DORA_C)
        private val TURIN_CENTRO_A = Route(GhostRoutes.TURIN_CENTRO_A)
        private val TURIN_CENTRO_B = Route(GhostRoutes.TURIN_CENTRO_B)
        private val TURIN_PORTA_SUSA = Route(GhostRoutes.TURIN_PORTA_SUSA)
        private val TURIN_VALENTINO_A = Route(GhostRoutes.TURIN_VALENTINO_A)
        private val TURIN_VANCHIGLIA = Route(GhostRoutes.TURIN_VANCHIGLIA)
        private val TURIN_REGINA = Route(GhostRoutes.TURIN_REGINA)
        private val TURIN_LINGOTTO = Route(GhostRoutes.TURIN_LINGOTTO)

        // ---------------------------------------------------------------
        // Torino — 20 simulated travelers spread across the whole city:
        // north (dept / Parco Dora), centre (Castello, Mole, Porta Nuova),
        // river (Valentino, Gran Madre) and south (Lingotto).
        // ---------------------------------------------------------------
        private val TURIN = Zone(
            listOf(
                Persona(
                    userId = "ghost-turin-1",
                    name = "Sofia",
                    description = "Studente di Informatica che raggiunge le aule al Dipartimento e poi fa una passeggiata al Parco Dora.",
                    city = "Torino",
                    isOnlineNow = true,
                    route = TURIN_ROUTE_A,
                    speedMetersPerSecond = 1.4,
                    mediaTitles = listOf("photo" to "Lezioni, Corso Svizzera", "photo" to "Parco Dora"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-1"),
                    transports = DemoGhostVisits.transportsByUser.getValue("ghost-turin-1")
                ),
                Persona(
                    userId = "ghost-turin-2",
                    name = "Marco",
                    description = "Corridore serale che fa il giro del Parco Dora lungo la Dora Riparia.",
                    city = "Torino",
                    isOnlineNow = true,
                    route = TURIN_ROUTE_B,
                    speedMetersPerSecond = 3.0,
                    mediaTitles = listOf("video" to "Corsa, ponte Dora", "photo" to "Tramonto sul parco"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-2")
                ),
                Persona(
                    userId = "ghost-turin-3",
                    name = "Giulia",
                    description = "Ex studentessa di passaggio a Torino: ha rivisto i luoghi della tesi vicino alla facoltà di Informatica.",
                    city = "Torino",
                    isOnlineNow = false,
                    route = TURIN_ROUTE_A,
                    speedMetersPerSecond = 1.2,
                    mediaTitles = listOf("photo" to "Ricordo di corso Svizzera"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-3")
                ),
                Persona(
                    userId = "ghost-turin-4",
                    name = "Chiara",
                    description = "Runner del parco: due giri di Parco Dora prima di cena, spesso con gli auricolari.",
                    city = "Torino",
                    isOnlineNow = false,
                    route = TURIN_ROUTE_B,
                    speedMetersPerSecond = 2.6,
                    mediaTitles = listOf("photo" to "Giro serale, Parco Dora"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-4")
                ),
                Persona(
                    userId = "ghost-turin-5",
                    name = "Elena",
                    description = "Turista in centro: Museo Egizio al mattino, poi un caffè in Piazza San Carlo e la passeggiata verso Porta Nuova.",
                    city = "Torino",
                    isOnlineNow = true,
                    route = TURIN_CENTRO_A,
                    speedMetersPerSecond = 1.1,
                    mediaTitles = listOf("photo" to "Museo Egizio", "photo" to "Piazza San Carlo"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-5"),
                    transports = DemoGhostVisits.transportsByUser.getValue("ghost-turin-5")
                ),
                Persona(
                    userId = "ghost-turin-6",
                    name = "Matteo",
                    description = "Pendolare che taglia Via Roma ogni mattina per prendere il treno a Porta Nuova.",
                    city = "Torino",
                    isOnlineNow = false,
                    route = TURIN_CENTRO_A,
                    speedMetersPerSecond = 1.5,
                    mediaTitles = listOf("photo" to "Porta Nuova di corsa"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-6"),
                    transports = DemoGhostVisits.transportsByUser.getValue("ghost-turin-6")
                ),
                Persona(
                    userId = "ghost-turin-7",
                    name = "Francesca",
                    description = "Visita alla Mole Antonelliana e poi aperitivo in Piazza Vittorio.",
                    city = "Torino",
                    isOnlineNow = true,
                    route = TURIN_CENTRO_B,
                    speedMetersPerSecond = 1.2,
                    mediaTitles = listOf("photo" to "Mole Antonelliana", "video" to "Panorama da Piazza Vittorio"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-7"),
                    transports = DemoGhostVisits.transportsByUser.getValue("ghost-turin-7")
                ),
                Persona(
                    userId = "ghost-turin-8",
                    name = "Davide",
                    description = "Giù da Corso Regina per una passeggiata fino al Duomo e alla Mole.",
                    city = "Torino",
                    isOnlineNow = false,
                    route = TURIN_CENTRO_B,
                    speedMetersPerSecond = 1.3,
                    mediaTitles = listOf("photo" to "Via Po"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-8")
                ),
                Persona(
                    userId = "ghost-turin-9",
                    name = "Alice",
                    description = "Domenica al Parco del Valentino, tra il castello e i Murazzi sul Po.",
                    city = "Torino",
                    isOnlineNow = true,
                    route = TURIN_VALENTINO_A,
                    speedMetersPerSecond = 1.1,
                    mediaTitles = listOf("photo" to "Castello del Valentino", "photo" to "Murazzi"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-9")
                ),
                Persona(
                    userId = "ghost-turin-10",
                    name = "Stefano",
                    description = "Corre lungo il Po dal Valentino ai Murazzi a passo sostenuto.",
                    city = "Torino",
                    isOnlineNow = false,
                    route = TURIN_VALENTINO_A,
                    speedMetersPerSecond = 3.2,
                    mediaTitles = listOf("video" to "Corsa lungo il Po"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-10")
                ),
                Persona(
                    userId = "ghost-turin-11",
                    name = "Andrea",
                    description = "Dal treno di Porta Susa a un colloquio in centro, passando da Piazza Statuto.",
                    city = "Torino",
                    isOnlineNow = true,
                    route = TURIN_PORTA_SUSA,
                    speedMetersPerSecond = 1.4,
                    mediaTitles = listOf("photo" to "Piazza Statuto"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-11"),
                    transports = DemoGhostVisits.transportsByUser.getValue("ghost-turin-11")
                ),
                Persona(
                    userId = "ghost-turin-12",
                    name = "Martina",
                    description = "Ex studentessa in città per un weekend: da Porta Susa a spasso fino a Palazzo Reale.",
                    city = "Torino",
                    isOnlineNow = false,
                    route = TURIN_PORTA_SUSA,
                    speedMetersPerSecond = 1.0,
                    mediaTitles = listOf("photo" to "Palazzo Reale"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-12")
                ),
                Persona(
                    userId = "ghost-turin-13",
                    name = "Riccardo",
                    description = "Passeggiata tra Porta Palazzo e il Regio Parco dopo il mercato.",
                    city = "Torino",
                    isOnlineNow = true,
                    route = TURIN_REGINA,
                    speedMetersPerSecond = 1.2,
                    mediaTitles = listOf("photo" to "Porta Palatina", "photo" to "Regio Parco"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-13"),
                    transports = DemoGhostVisits.transportsByUser.getValue("ghost-turin-13")
                ),
                Persona(
                    userId = "ghost-turin-14",
                    name = "Silvia",
                    description = "Torna a casa a piedi dopo il mercato di Porta Palazzo.",
                    city = "Torino",
                    isOnlineNow = false,
                    route = TURIN_REGINA,
                    speedMetersPerSecond = 1.1,
                    mediaTitles = listOf("photo" to "Bancarelle di Porta Palazzo"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-14")
                ),
                Persona(
                    userId = "ghost-turin-15",
                    name = "Giorgio",
                    description = "Va a vedere la pista sul tetto del Lingotto, a piedi da Porta Nuova.",
                    city = "Torino",
                    isOnlineNow = true,
                    route = TURIN_LINGOTTO,
                    speedMetersPerSecond = 1.3,
                    mediaTitles = listOf("photo" to "Lingotto", "video" to "Pista sul tetto"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-15")
                ),
                Persona(
                    userId = "ghost-turin-16",
                    name = "Valentina",
                    description = "Ciclista urbana sulla direttrice Porta Nuova–Lingotto, sfreccia senza fretta.",
                    city = "Torino",
                    isOnlineNow = true,
                    route = TURIN_LINGOTTO,
                    speedMetersPerSecond = 2.4,
                    mediaTitles = listOf("photo" to "Via Nizza"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-16")
                ),
                Persona(
                    userId = "ghost-turin-17",
                    name = "Federico",
                    description = "Tragitto di ieri: Piazza Vittorio, ponte e salita alla Gran Madre.",
                    city = "Torino",
                    isOnlineNow = false,
                    route = TURIN_VANCHIGLIA,
                    speedMetersPerSecond = 1.1,
                    mediaTitles = listOf("photo" to "Gran Madre"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-17"),
                    transports = DemoGhostVisits.transportsByUser.getValue("ghost-turin-17")
                ),
                Persona(
                    userId = "ghost-turin-18",
                    name = "Ilaria",
                    description = "Sosta al belvedere della Gran Madre prima di rientrare in centro.",
                    city = "Torino",
                    isOnlineNow = true,
                    route = TURIN_VANCHIGLIA,
                    speedMetersPerSecond = 1.0,
                    mediaTitles = listOf("photo" to "Belvedere Gran Madre"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-18")
                ),
                Persona(
                    userId = "ghost-turin-19",
                    name = "Paolo",
                    description = "Pausa pranzo camminando da Corso Mortara lungo la Dora fino a Valdocco.",
                    city = "Torino",
                    isOnlineNow = true,
                    route = TURIN_PARCO_DORA_C,
                    speedMetersPerSecond = 1.3,
                    mediaTitles = listOf("photo" to "Dora, zona Valdocco"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-19")
                ),
                Persona(
                    userId = "ghost-turin-20",
                    name = "Federica",
                    description = "Passeggiata di ieri sul lungofiume della Dora tra Mortara e il parco.",
                    city = "Torino",
                    isOnlineNow = false,
                    route = TURIN_PARCO_DORA_C,
                    speedMetersPerSecond = 1.0,
                    mediaTitles = listOf("video" to "Lungo Dora"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-turin-20")
                )
            )
        )

        private val CERIGNOLA_ROUTE_A = Route(GhostRoutes.CERIGNOLA_ROUTE_A)
        private val CERIGNOLA_ROUTE_B = Route(GhostRoutes.CERIGNOLA_ROUTE_B)
        private val CERIGNOLA_ROUTE_C = Route(GhostRoutes.CERIGNOLA_ROUTE_C)
        private val CERIGNOLA_ROUTE_D = Route(GhostRoutes.CERIGNOLA_ROUTE_D)
        private val CERIGNOLA_ROUTE_E = Route(GhostRoutes.CERIGNOLA_ROUTE_E)

        // ---------------------------------------------------------------
        // Cerignola — 5 simulated travelers in the city centre and the
        // surrounding area (station, Ripalta sanctuary).
        // ---------------------------------------------------------------
        private val CERIGNOLA = Zone(
            listOf(
                Persona(
                    userId = "ghost-cerignola-1",
                    name = "Angela",
                    description = "Pomeriggio in centro: dal Duomo a Piazza della Repubblica, poi una sosta in Villa Comunale.",
                    city = "Cerignola",
                    isOnlineNow = true,
                    route = CERIGNOLA_ROUTE_A,
                    speedMetersPerSecond = 1.1,
                    mediaTitles = listOf("photo" to "Cattedrale", "photo" to "Villa Comunale"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-cerignola-1")
                ),
                Persona(
                    userId = "ghost-cerignola-2",
                    name = "Vito",
                    description = "Camminata sulla statale verso il Santuario della Madonna di Ripalta.",
                    city = "Cerignola",
                    isOnlineNow = false,
                    route = CERIGNOLA_ROUTE_B,
                    speedMetersPerSecond = 1.3,
                    mediaTitles = listOf("photo" to "Santuario di Ripalta"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-cerignola-2")
                ),
                Persona(
                    userId = "ghost-cerignola-3",
                    name = "Maria",
                    description = "Prende il treno: dal Duomo cammina fino alla stazione di Cerignola Città.",
                    city = "Cerignola",
                    isOnlineNow = true,
                    route = CERIGNOLA_ROUTE_C,
                    speedMetersPerSecond = 1.2,
                    mediaTitles = listOf("photo" to "Stazione di Cerignola Città"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-cerignola-3"),
                    transports = DemoGhostVisits.transportsByUser.getValue("ghost-cerignola-3")
                ),
                Persona(
                    userId = "ghost-cerignola-4",
                    name = "Giovanni",
                    description = "Passeggiata di ieri dalla Villa Comunale verso la zona nord, oltre corso Garibaldi.",
                    city = "Cerignola",
                    isOnlineNow = false,
                    route = CERIGNOLA_ROUTE_D,
                    speedMetersPerSecond = 1.0,
                    mediaTitles = listOf("photo" to "Villa Comunale Umberto I"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-cerignola-4")
                ),
                Persona(
                    userId = "ghost-cerignola-5",
                    name = "Teresa",
                    description = "Giro del centro storico: Duomo, Piazza della Repubblica e una granita in Villa Comunale.",
                    city = "Cerignola",
                    isOnlineNow = true,
                    route = CERIGNOLA_ROUTE_E,
                    speedMetersPerSecond = 1.1,
                    mediaTitles = listOf("photo" to "Piazza della Repubblica", "video" to "Villa Comunale"),
                    visits = DemoGhostVisits.visitsByUser.getValue("ghost-cerignola-5"),
                    transports = DemoGhostVisits.transportsByUser.getValue("ghost-cerignola-5")
                )
            )
        )
    }
}