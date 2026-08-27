#!/usr/bin/env python3
"""
Fetches real street geometry for the demo ghost routes and generates
GhostRoutes.kt.

Why: the app simulates other travelers locally (spec §5.3bis, no backend), so
the routes must be static data. This script queries the public OSRM foot
routing service (router.project-osrm.org, OpenStreetMap data) once at dev time
and writes the resulting street-following waypoints into a Kotlin file. The app
itself never touches the network for this.

Usage: python scripts/fetch_ghost_routes.py
Output: app/src/main/java/com/example/progmob_progetto_melluso/map/GhostRoutes.kt
"""

import json
import math
import urllib.request

OUT_FILE = "app/src/main/java/com/example/progmob_progetto_melluso/map/GhostRoutes.kt"

# Routes as (name, [(lon, lat), ...]) — ordered stops to chain in one request.
ROUTES = {
    # --- Turin: Dipartimento di Informatica / Parco Dora (north) ---
    "TURIN_ROUTE_A": [
        (7.6576, 45.0918),   # Corso Svizzera, north of the Informatics dept
        (7.6667, 45.0897),   # Parco Dora, area Michelin
    ],
    "TURIN_ROUTE_B": [
        (7.6667, 45.0897),   # Parco Dora, area Michelin
        (7.6660, 45.0921),   # Parco Dora, area Vitali
        (7.6644, 45.0928),   # Lungo Dora
        (7.6667, 45.0897),   # back to Michelin (loop)
    ],
    "TURIN_PARCO_DORA_C": [
        (7.6643, 45.0958),   # Corso Mortara, north side
        (7.6706, 45.0936),   # Parco Dora, area Valdocco
    ],
    # --- Turin: city centre ---
    "TURIN_CENTRO_A": [
        (7.6862, 45.0709),   # Piazza Castello
        (7.6821, 45.0679),   # Piazza San Carlo
        (7.6797, 45.0618),   # Porta Nuova station
    ],
    "TURIN_CENTRO_B": [
        (7.6862, 45.0709),   # Piazza Castello
        (7.6934, 45.0689),   # Mole Antonelliana
        (7.6973, 45.0627),   # Piazza Vittorio Veneto
    ],
    "TURIN_PORTA_SUSA": [
        (7.6646, 45.0711),   # Porta Susa station
        (7.6714, 45.0719),   # Piazza Statuto
        (7.6862, 45.0709),   # Piazza Castello
    ],
    # --- Turin: river / parks / south ---
    "TURIN_VALENTINO_A": [
        (7.6880, 45.0548),   # Castello del Valentino (park entrance)
        (7.6952, 45.0643),   # Murazzi del Po
    ],
    "TURIN_VANCHIGLIA": [
        (7.6973, 45.0627),   # Piazza Vittorio Veneto
        (7.7055, 45.0614),   # Gran Madre di Dio
    ],
    "TURIN_REGINA": [
        (7.6862, 45.0766),   # Porta Palazzo / Balon
        (7.7095, 45.0829),   # Regio Parco
    ],
    "TURIN_LINGOTTO": [
        (7.6797, 45.0618),   # Porta Nuova station
        (7.6650, 45.0330),   # Lingotto (former Fiat factory)
    ],
    # --- Cerignola ---
    "CERIGNOLA_ROUTE_A": [
        (15.8928, 41.2665),  # Chiesa San Francesco d'Assisi
        (15.8948, 41.2653),  # Chiesa del Purgatorio
        (15.8995, 41.2642),  # Cattedrale San Pietro (Piazza Duomo)
        (15.9040, 41.2656),  # Piazza della Repubblica
        (15.9035, 41.2632),  # Villa Comunale Umberto I
    ],
    "CERIGNOLA_ROUTE_B": [
        (15.8995, 41.2642),  # Cattedrale
        (15.8992, 41.2308),  # Santuario Madonna di Ripalta
    ],
    "CERIGNOLA_ROUTE_C": [
        (15.8995, 41.2642),  # Cattedrale
        (15.9066, 41.2629),  # Stazione di Cerignola Città
    ],
    "CERIGNOLA_ROUTE_D": [
        (15.9035, 41.2632),  # Villa Comunale Umberto I
        (15.8952, 41.2689),  # north area (Ospedale)
    ],
    "CERIGNOLA_ROUTE_E": [
        (15.8995, 41.2642),  # Cattedrale
        (15.9040, 41.2656),  # Piazza della Repubblica
        (15.9035, 41.2632),  # Villa Comunale Umberto I
    ],
}

# Keep a waypoint only when it is at least this far (meters) from the previous
# kept one: OSRM emits sub-meter vertices that would only bloat the file.
MIN_SEGMENT_METERS = 10.0


def haversine(lat1, lon1, lat2, lon2):
    r = 6_371_000.0
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2) ** 2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2) ** 2
    return r * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def fetch_route(name, stops):
    coords = ";".join(f"{lon},{lat}" for lon, lat in stops)
    url = (
        "https://router.project-osrm.org/route/v1/foot/"
        + coords
        + "?overview=full&geometries=geojson"
    )
    with urllib.request.urlopen(url, timeout=60) as resp:
        data = json.load(resp)
    if data.get("code") != "Ok" or not data.get("routes"):
        raise RuntimeError(f"{name}: OSRM returned {data.get('code')}")
    geometry = data["routes"][0]["geometry"]["coordinates"]  # [[lon, lat], ...]
    distance = data["routes"][0]["distance"]
    return geometry, distance


def decimate(geometry):
    kept = []
    last = None
    for lon, lat in geometry:
        if last is None or haversine(last[1], last[0], lat, lon) >= MIN_SEGMENT_METERS:
            kept.append((lat, lon))  # convert to (lat, lon) as used by the app
            last = (lon, lat)
    return kept


def main():
    generated = {}
    for name, stops in ROUTES.items():
        geometry, distance = fetch_route(name, stops)
        waypoints = decimate(geometry)
        generated[name] = waypoints
        total = sum(
            haversine(a[0], a[1], b[0], b[1])
            for a, b in zip(waypoints, waypoints[1:])
        )
        print(f"{name}: {distance:.0f} m routed, {len(waypoints)} waypoints, {total:.0f} m embedded")

    lines = []
    lines.append("package com.example.progmob_progetto_melluso.map")
    lines.append("")
    lines.append("/**")
    lines.append(" * Real street waypoints of the demo ghost routes.")
    lines.append(" *")
    lines.append(" * These are (lat, lon) points fetched once from the public OSRM")
    lines.append(" * foot-routing API (router.project-osrm.org, OpenStreetMap data):")
    lines.append(" * the polylines follow actual walkable streets instead of straight")
    lines.append(" * lines through buildings. They are static so the local,")
    lines.append(" * deterministic simulation (spec §5.3bis) stays fully offline.")
    lines.append(" * Regenerate with: python scripts/fetch_ghost_routes.py")
    lines.append(" */")
    lines.append("object GhostRoutes {")
    for name, waypoints in generated.items():
        lines.append("")
        lines.append(f"    val {name}: List<Pair<Double, Double>> = listOf(")
        for i in range(0, len(waypoints), 4):
            chunk = waypoints[i : i + 4]
            lines.append(
                "        "
                + ", ".join(f"{lat:.6f} to {lon:.6f}" for lat, lon in chunk)
                + ("," if i + 4 < len(waypoints) else "")
            )
        lines.append("    )")
    lines.append("}")
    lines.append("")

    with open(OUT_FILE, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(f"Wrote {OUT_FILE}")


if __name__ == "__main__":
    main()
