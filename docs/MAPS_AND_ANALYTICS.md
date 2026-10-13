# Maps and Analytics

Authoritative for: map rendering and caching, geometry, playback, metric definitions, aggregation, places, routes, and heatmaps. The map UI is specified in [UI_UX_SPEC.md](UI_UX_SPEC.md).

## 1. Map policy

MapLibre Native renders two bundled Atlas styles (light and dark) over OpenFreeMap/OpenMapTiles-compatible vector tiles. Network is permitted only for HTTPS style/tile requests to the configured tile host; attribution is permanently reachable from every map. A missing tile never hides the route: show a plain canvas, route overlays, scale, north marker, attribution button, and “Map tiles unavailable offline”. No geocoding, reverse geocoding, routing, or map matching occurs without separately documented consent.

The visual basemap is low-contrast graphite: land near the surface colour; water one neutral step darker; parks and buildings only if useful at the current zoom; roads hairline grey; labels sparse and charcoal. No satellite imagery, traffic, 3D buildings, terrain, gradients, or decorative POIs. Route layers are data overlays and remain legible with tiles absent.

## 2. Geometry pipeline

Raw points stay immutable. For each segment, a quality-filtered line is built in capture order, then Douglas–Peucker simplified at 0.5 m (zoom ≥17), 2 m (14–16), 8 m (11–13), 35 m (≤10). Store encoded polyline precision 6 and bounding box per level in `geometry_cache`; never simplify across a GAP. Draw a thin dashed connector for a GAP only between known endpoints, tagged “Missing data”; otherwise draw no connector.

`AtlasMap` accepts `MapRenderModel(viewport, routeLayers, markers, livePosition?, privacyMask?, interactionMode)`. It owns MapLibre lifecycle and camera only; features supply data in screen-independent coordinates. Overlay diffing uses stable IDs and updates sources on the map thread. Route hit testing is a 24 dp invisible corridor, resolved to nearest segment; markers have 48 dp targets.

## 3. Route encoding and playback

| Element | Light / dark treatment | Pattern |
|---|---|---|
| Walk / run | charcoal / near-white | solid 4 dp; run has 2 dp dot interval |
| Bicycle | graphite / light-grey | solid 4 dp with short cross-ticks |
| Motorbike / scooter | black / white | solid 5 dp |
| Car | black / white | solid 4 dp |
| Bus | black / white | 8:3 dash |
| Train / metro | black / white | 2:2 dash, square caps |
| Unknown | mid-grey | 2:4 dot |
| GAP | mid-grey | 6:6 dash, 2 dp, never implies travel |

Do not rely on colour: line weight/dash and the legend distinguish modes. Stops are hollow circles; transitions are 8 dp filled squares; current location is a 10 dp dot and accuracy is a 1 dp circle with 12% opacity. Playback interpolates only along recorded/smoothed points within a non-gap MOVE segment. At gaps it pauses, labels “No recorded position”, then jumps to the next observed point. Scrubbing is deterministic and never invents a path.

## 4. Cache and offline regions

Ambient MapLibre cache default is 256 MB, LRU, configurable 64 MB–1 GB; it is treated as disposable. Offline regions use the MapLibre offline API with user-selected bounding box and zoom 10–16, a visible byte estimate, Wi-Fi-only default, pause/resume/delete, and a 2 GB per-region guard. Downloaded map data is separate from Atlas location data and is removed by “Clear map cache”, not by “Delete all Atlas data” unless selected. PMTiles is deferred; its cache/offline limitations need separate design validation.

## 5. Metric definitions and display precision

| Metric | Definition | Display |
|---|---|---|
| Distance | sum of accepted MOVE segment geodesic distances; GAP excludes distance | `<10 km`: metres rounded to 10 m; otherwise km to 0.1; ≥100 km whole km |
| Moving time | sum of MOVE segment interval minus stopped time | `m` below 1 h; `h m` thereafter |
| Stopped time | pauses and STOPs internal to journeys | nearest minute |
| Coverage | `covered_ms / enabled_ms`, where covered is live permitted time with expected fixes or confirmed stationary | whole percent; “Not enough enabled time” below 30 min |
| Uncertain distance | MOVE distance where posterior <0.55 or mode is UNKNOWN | whole percent of distance, then detail metres |
| Speed | segment distance / moving duration, never include stops/gaps | km/h to one decimal under 20, whole otherwise |
| Trend | `(current - previous comparable period) / previous`; unavailable if previous has <50% coverage | whole percent with direction word |

All period metrics include only `CONFIRMED` non-deleted journeys. A day is local date by capture `zone_id`; a journey crossing midnight contributes clipped segment portions to each day. Period summaries sum daily values; never average averages. Median journey duration is median of complete confirmed journeys started in period. “Estimated” appears whenever coverage <90%, uncertain distance >10%, imported data is included, or an aggregate includes a GAP.

## 6. Aggregates, charts, and records

Every derived write recomputes affected local days in the same transaction. Week begins Monday in the display locale; month/year follow local calendar; lifetime spans first to last confirmed journey. Mode share denominator excludes gaps and deleted journeys. Records are calculated from confirmed journeys/days with ≥90% coverage; ties list all dates/journeys, not arbitrary winner.

Charts use a zero baseline for absolute measures, labelled axes, direct value in focus state, and no smoothing. Bars are 12 dp wide minimum with 4 dp separation; line charts use one 2 dp stroke and a single focus rule. Hour × weekday is a labelled 7×24 grid using five discrete neutral fills and a text alternative table for TalkBack. Six-month trend uses monthly bars; an incomplete current month is hatched and excluded from comparison.

## 7. Places, routes, and heatmap

Candidate place clustering runs only on `VISIT` centroids. DBSCAN with 75 m epsilon and minimum three visits across two calendar days produces candidates. A confirmed place’s radius is user-owned (25–500 m); auto clustering never moves it. Assignment chooses the nearest confirmed place whose radius contains the visit, breaking ties by smaller radius then oldest creation. Ignore suppresses a candidate fingerprint for 180 days.

Recurring routes require ≥3 confirmed journeys with the same confirmed origin and destination, same primary mode family, and Frechet-like simplified-polyline similarity ≥0.75 after length normalisation. Suggestions are not analytics categories until confirmed. Route comparisons align by elapsed journey percentage and show distance/time variation, never a fabricated common route.

Heatmap bins accepted MOVE points into geohash-7 cells with one count per journey per cell (avoids slow-route dominance). It defaults to all time, 50% opacity, 5 discrete neutral levels, and excludes privacy-zone interiors in screenshot-safe mode. It is an aggregate density layer, not a claim of exact path frequency.
