package ru.shturman.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

/**
 * Свой тёмный стиль 3D-карты. Данные: OpenStreetMap через бесплатные тайлы OpenFreeMap.
 * Здания выдавливаются по высоте. На односторонних дорогах нарисованы стрелки по направлению
 * движения, на двусторонних — пунктирная осевая линия.
 * Все цвета можно менять прямо здесь.
 */
object MapStyle {

    const val ONEWAY_IMAGE = "oneway"
    const val HEART_IMAGE = "heart"

    /** Сердечко для следа в режиме принцессы. */
    fun heartBitmap(): Bitmap {
        val size = 48
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F2709F"); style = Paint.Style.FILL }
        val path = Path().apply {
            moveTo(24f, 42f)
            cubicTo(2f, 26f, 6f, 6f, 24f, 15f)
            cubicTo(42f, 6f, 46f, 26f, 24f, 42f)
            close()
        }
        c.drawPath(path, p)
        return bmp
    }

    private class Tint(val id: String, val kind: String, val normal: String, val princess: String)

    private val TINTS = listOf(
        Tint("bg", "bg", "#0A0C10", "#FBEFF7"),
        Tint("grass", "fill", "#0C120F", "#F3E6F4"),
        Tint("wood", "fill", "#0D1612", "#E7DDF5"),
        Tint("residential", "fill", "#0D1015", "#F9E3EF"),
        Tint("park-flat", "fill", "#0D1612", "#E3E0F6"),
        Tint("water", "fill", "#0B1823", "#CFDCF7"),
        Tint("waterway", "line", "#0B1823", "#CFDCF7"),
        Tint("road-minor", "line", "#242A33", "#FFFFFF"),
        Tint("road-mid", "line", "#323A45", "#FFFFFF"),
        Tint("road-major", "line", "#46515E", "#F6D28B"),
        Tint("centerline", "line", "#7D8896", "#E9BBD6"),
        Tint("centerline-minor", "line", "#5A6572", "#EBCBE0"),
        Tint("route-casing", "line", "#0A0C10", "#FFF4FA"),
        Tint("building-3d", "ext", "#1C222B", "#E6C7E8"),
        Tint("road-names", "text", "#6B737D", "#9A6C9F"),
        Tint("place-names", "text", "#8A9099", "#7A4A80"),
        Tint("landmark-names", "text", "#FFD98A", "#B07A10"),
        Tint("church-names", "text", "#D9C4FF", "#8A5CC0"),
        Tint("landmark-dots", "dot", "#FFC857", "#F0B33C"),
        Tint("church-dots", "dot", "#C9A8FF", "#B79CF2")
    )

    /** Перекрашивает карту: тёмная обычная или светлая розово-лавандовая для режима принцессы. */
    fun applyTheme(style: org.maplibre.android.maps.Style, princess: Boolean) {
        val pf = org.maplibre.android.style.layers.PropertyFactory
        for (t in TINTS) {
            val layer = style.getLayer(t.id) ?: continue
            val c = if (princess) t.princess else t.normal
            val halo = if (princess) "#FFF4FA" else "#0A0C10"
            val stroke = if (princess) "#FFF4FA" else "#0A0C10"
            when (t.kind) {
                "bg" -> layer.setProperties(pf.backgroundColor(c))
                "fill" -> layer.setProperties(pf.fillColor(c))
                "line" -> layer.setProperties(pf.lineColor(c))
                "ext" -> layer.setProperties(pf.fillExtrusionColor(c))
                "text" -> layer.setProperties(pf.textColor(c), pf.textHaloColor(halo))
                "dot" -> layer.setProperties(pf.circleColor(c), pf.circleStrokeColor(stroke))
            }
        }
        style.getLayer("route-line")?.setProperties(pf.lineColor(if (princess) "#F2709F" else "#3DDCFF"))
        style.addImage(ONEWAY_IMAGE, onewayBitmap(princess))
        val vis = org.maplibre.android.style.layers.Property.VISIBLE
        val none = org.maplibre.android.style.layers.Property.NONE
        style.getLayer("trail-smoke")?.setProperties(pf.visibility(if (princess) none else vis))
        style.getLayer("trail-hearts")?.setProperties(pf.visibility(if (princess) vis else none))
    }

    /** Стрелка-шеврон, смотрит вправо. Вдоль дороги Карта сама поворачивает её по направлению движения. */
    fun onewayBitmap(princess: Boolean = false): Bitmap {
        val size = 32
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(if (princess) "#CCF2709F" else "#CC3DDCFF")
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val path = Path().apply {
            moveTo(10f, 7f)
            lineTo(22f, 16f)
            lineTo(10f, 25f)
        }
        c.drawPath(path, p)
        return bmp
    }

    val JSON: String = """
{
  "version": 8,
  "name": "Shturman Dark 3D",
  "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
  "light": { "anchor": "viewport", "color": "#9FB4CC", "intensity": 0.4, "position": [1.2, 210, 40] },
  "sources": {
    "omt": {
      "type": "vector",
      "url": "https://tiles.openfreemap.org/planet",
      "attribution": "<a href=\"https://openfreemap.org\">OpenFreeMap</a> © <a href=\"https://www.openmaptiles.org/\">OpenMapTiles</a> Data from <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>"
    },
    "route": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "dest": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "bumps": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "works": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "danger": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "caravan": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "trail": { "type": "geojson", "data": { "type": "FeatureCollection", "features": [] } },
    "sat": { "type": "raster", "tileSize": 256, "maxzoom": 19,
             "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
             "attribution": "Esri, Maxar, Earthstar Geographics" }
  },
  "layers": [
    { "id": "bg", "type": "background", "paint": { "background-color": "#0A0C10" } },

    { "id": "grass", "type": "fill", "source": "omt", "source-layer": "landcover",
      "filter": ["in", "class", "grass", "farmland"],
      "paint": { "fill-color": "#0C120F" } },

    { "id": "wood", "type": "fill", "source": "omt", "source-layer": "landcover",
      "filter": ["==", "class", "wood"],
      "paint": { "fill-color": "#0D1612" } },

    { "id": "residential", "type": "fill", "source": "omt", "source-layer": "landuse",
      "filter": ["in", "class", "residential", "suburb", "neighbourhood"],
      "paint": { "fill-color": "#0D1015" } },

    { "id": "park-flat", "type": "fill", "source": "omt", "source-layer": "park",
      "paint": { "fill-color": "#0D1612" } },

    { "id": "water", "type": "fill", "source": "omt", "source-layer": "water",
      "paint": { "fill-color": "#0B1823" } },

    { "id": "waterway", "type": "line", "source": "omt", "source-layer": "waterway",
      "paint": { "line-color": "#0B1823", "line-width": ["interpolate", ["linear"], ["zoom"], 10, 0.5, 16, 3] } },

    { "id": "road-minor", "type": "line", "source": "omt", "source-layer": "transportation",
      "filter": ["in", "class", "minor", "service", "track"],
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "#242A33",
                 "line-width": ["interpolate", ["exponential", 1.5], ["zoom"], 12, 0.3, 14, 1.2, 18, 7, 20, 16] } },

    { "id": "road-mid", "type": "line", "source": "omt", "source-layer": "transportation",
      "filter": ["in", "class", "tertiary", "secondary"],
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "#323A45",
                 "line-width": ["interpolate", ["exponential", 1.5], ["zoom"], 10, 0.4, 14, 2, 18, 10, 20, 22] } },

    { "id": "road-major", "type": "line", "source": "omt", "source-layer": "transportation",
      "filter": ["in", "class", "primary", "trunk", "motorway"],
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "#46515E",
                 "line-width": ["interpolate", ["exponential", 1.5], ["zoom"], 8, 0.5, 14, 2.8, 18, 13, 20, 28] } },

    { "id": "centerline", "type": "line", "source": "omt", "source-layer": "transportation", "minzoom": 14,
      "filter": ["all", ["in", "class", "tertiary", "secondary", "primary", "trunk"],
                 ["!=", "oneway", 1], ["!=", "oneway", -1]],
      "paint": { "line-color": "#7D8896", "line-dasharray": [3, 3],
                 "line-width": ["interpolate", ["linear"], ["zoom"], 14, 0.3, 18, 1.4] } },

    { "id": "centerline-minor", "type": "line", "source": "omt", "source-layer": "transportation", "minzoom": 16,
      "filter": ["all", ["==", "class", "minor"], ["!=", "oneway", 1], ["!=", "oneway", -1]],
      "paint": { "line-color": "#5A6572", "line-dasharray": [3, 3],
                 "line-width": ["interpolate", ["linear"], ["zoom"], 16, 0.4, 19, 1.2] } },

    { "id": "sat-layer", "type": "raster", "source": "sat", "layout": { "visibility": "none" } },

    { "id": "route-casing", "type": "line", "source": "route",
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "#0A0C10", "line-opacity": 0.85,
                 "line-width": ["interpolate", ["linear"], ["zoom"], 10, 5, 16, 10, 19, 15] } },

    { "id": "route-line", "type": "line", "source": "route",
      "layout": { "line-cap": "round", "line-join": "round" },
      "paint": { "line-color": "#3DDCFF",
                 "line-width": ["interpolate", ["linear"], ["zoom"], 10, 3, 16, 6, 19, 9] } },

    { "id": "building-3d", "type": "fill-extrusion", "source": "omt", "source-layer": "building", "minzoom": 13,
      "paint": {
        "fill-extrusion-color": "#1C222B",
        "fill-extrusion-height": ["coalesce", ["get", "render_height"], 8],
        "fill-extrusion-base": ["coalesce", ["get", "render_min_height"], 0],
        "fill-extrusion-opacity": 0.92 } },

    { "id": "oneway-forward", "type": "symbol", "source": "omt", "source-layer": "transportation", "minzoom": 15,
      "filter": ["all", ["==", "oneway", 1],
                 ["in", "class", "minor", "tertiary", "secondary", "primary", "trunk", "motorway"]],
      "layout": { "symbol-placement": "line", "symbol-spacing": 60,
                  "icon-image": "oneway",
                  "icon-size": ["interpolate", ["linear"], ["zoom"], 15, 0.5, 19, 1.0],
                  "icon-rotation-alignment": "map", "icon-pitch-alignment": "map",
                  "icon-keep-upright": false, "icon-allow-overlap": true } },

    { "id": "oneway-backward", "type": "symbol", "source": "omt", "source-layer": "transportation", "minzoom": 15,
      "filter": ["all", ["==", "oneway", -1],
                 ["in", "class", "minor", "tertiary", "secondary", "primary", "trunk", "motorway"]],
      "layout": { "symbol-placement": "line", "symbol-spacing": 60,
                  "icon-image": "oneway", "icon-rotate": 180,
                  "icon-size": ["interpolate", ["linear"], ["zoom"], 15, 0.5, 19, 1.0],
                  "icon-rotation-alignment": "map", "icon-pitch-alignment": "map",
                  "icon-keep-upright": false, "icon-allow-overlap": true } },

    { "id": "road-names", "type": "symbol", "source": "omt", "source-layer": "transportation_name", "minzoom": 15,
      "layout": { "symbol-placement": "line", "text-field": ["coalesce", ["get", "name:ru"], ["get", "name"]],
                  "text-font": ["Noto Sans Regular"], "text-size": 11 },
      "paint": { "text-color": "#6B737D", "text-halo-color": "#0A0C10", "text-halo-width": 1.5 } },

    { "id": "place-names", "type": "symbol", "source": "omt", "source-layer": "place",
      "maxzoom": 13.5,
      "filter": ["in", "class", "city", "town"],
      "layout": { "text-field": ["coalesce", ["get", "name:ru"], ["get", "name"]],
                  "text-font": ["Noto Sans Regular"], "text-size": 15 },
      "paint": { "text-color": "#8A9099", "text-halo-color": "#0A0C10", "text-halo-width": 1.5 } },

    { "id": "landmark-dots", "type": "circle", "source": "omt", "source-layer": "poi", "minzoom": 12,
      "filter": ["all", ["has", "name"], ["in", "class", "castle", "monument", "attraction", "museum", "historic"]],
      "paint": { "circle-radius": ["interpolate", ["linear"], ["zoom"], 12, 3, 16, 7],
                 "circle-color": "#FFC857", "circle-stroke-color": "#0A0C10", "circle-stroke-width": 1.5 } },

    { "id": "church-dots", "type": "circle", "source": "omt", "source-layer": "poi", "minzoom": 14,
      "filter": ["all", ["has", "name"], ["==", "class", "place_of_worship"]],
      "paint": { "circle-radius": ["interpolate", ["linear"], ["zoom"], 14, 2.5, 18, 6],
                 "circle-color": "#C9A8FF", "circle-stroke-color": "#0A0C10", "circle-stroke-width": 1.2 } },

    { "id": "landmark-names", "type": "symbol", "source": "omt", "source-layer": "poi", "minzoom": 12.5,
      "filter": ["all", ["has", "name"], ["in", "class", "castle", "monument", "attraction", "museum", "historic"]],
      "layout": { "text-field": ["coalesce", ["get", "name:ru"], ["get", "name"]],
                  "text-font": ["Noto Sans Regular"], "text-size": ["interpolate", ["linear"], ["zoom"], 12, 11, 17, 15],
                  "text-offset": [0, 0.9], "text-anchor": "top", "text-max-width": 8 },
      "paint": { "text-color": "#FFD98A", "text-halo-color": "#0A0C10", "text-halo-width": 1.6 } },

    { "id": "church-names", "type": "symbol", "source": "omt", "source-layer": "poi", "minzoom": 15.5,
      "filter": ["all", ["has", "name"], ["==", "class", "place_of_worship"]],
      "layout": { "text-field": ["coalesce", ["get", "name:ru"], ["get", "name"]],
                  "text-font": ["Noto Sans Regular"], "text-size": 12,
                  "text-offset": [0, 0.8], "text-anchor": "top", "text-max-width": 8 },
      "paint": { "text-color": "#D9C4FF", "text-halo-color": "#0A0C10", "text-halo-width": 1.5 } },

    { "id": "trail-hearts", "type": "symbol", "source": "trail",
      "layout": { "visibility": "none", "icon-image": "heart", "icon-allow-overlap": true, "icon-ignore-placement": true,
                  "icon-size": ["interpolate", ["linear"], ["get", "a"], 0, 0.3, 1, 0.75] },
      "paint": { "icon-opacity": ["*", 0.8, ["-", 1, ["get", "a"]]] } },

    { "id": "trail-smoke", "type": "circle", "source": "trail",
      "paint": { "circle-radius": ["interpolate", ["linear"], ["get", "a"], 0, 3, 1, 9],
                 "circle-color": "#A8BCC8", "circle-blur": 1,
                 "circle-opacity": ["*", 0.3, ["-", 1, ["get", "a"]]] } },

    { "id": "caravan-dots", "type": "circle", "source": "caravan",
      "paint": { "circle-radius": 9, "circle-color": ["case", ["==", ["get", "leader"], true], "#FFD400", "#7CFF6B"],
                 "circle-stroke-color": "#0A0C10", "circle-stroke-width": 2 } },

    { "id": "caravan-names", "type": "symbol", "source": "caravan",
      "layout": { "text-field": ["get", "name"], "text-font": ["Noto Sans Regular"], "text-size": 12,
                  "text-offset": [0, 1.4], "text-allow-overlap": true },
      "paint": { "text-color": "#FFFFFF", "text-halo-color": "#0A0C10", "text-halo-width": 1.5 } },

    { "id": "dest-pin", "type": "circle", "source": "dest",
      "paint": { "circle-radius": 8, "circle-color": "#FF2E2E",
                 "circle-stroke-color": "#FFFFFF", "circle-stroke-width": 2 } }
  ]
}
""".trimIndent()
}
