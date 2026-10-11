package ru.shturman.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Свой тёмный стиль 3D-карты. Данные: OpenStreetMap через бесплатные тайлы OpenFreeMap.
 * Здания выдавливаются по высоте, леса и парки покрыты символическими деревьями.
 * Все цвета можно менять прямо здесь.
 */
object MapStyle {

    const val TREE_IMAGE = "tree"

    /** Маленькое символическое дерево: тёмная крона с подсветкой и ствол. Рисуется кодом, без файлов. */
    fun treeBitmap(): Bitmap {
        val size = 48
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        c.drawColor(Color.parseColor("#0C130F"))   // фон леса

        p.color = Color.parseColor("#2B1F17")       // ствол
        c.drawRect(22f, 30f, 26f, 40f, p)

        p.color = Color.parseColor("#17301F")       // крона, тёмная часть
        c.drawCircle(24f, 22f, 12f, p)
        p.color = Color.parseColor("#1F4A2C")       // крона, светлая часть
        c.drawCircle(21f, 19f, 8f, p)
        p.color = Color.parseColor("#2A6A3C")       // блик
        c.drawCircle(19f, 16f, 3.5f, p)
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
    }
  },
  "layers": [
    { "id": "bg", "type": "background", "paint": { "background-color": "#0A0C10" } },

    { "id": "grass", "type": "fill", "source": "omt", "source-layer": "landcover",
      "filter": ["in", "class", "grass", "farmland"],
      "paint": { "fill-color": "#0C120F" } },

    { "id": "residential", "type": "fill", "source": "omt", "source-layer": "landuse",
      "filter": ["in", "class", "residential", "suburb", "neighbourhood"],
      "paint": { "fill-color": "#0D1015" } },

    { "id": "park-flat", "type": "fill", "source": "omt", "source-layer": "park",
      "paint": { "fill-color": "#0C130F" } },

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

    { "id": "wood-3d", "type": "fill-extrusion", "source": "omt", "source-layer": "landcover",
      "filter": ["==", "class", "wood"], "minzoom": 12,
      "paint": { "fill-extrusion-pattern": "tree", "fill-extrusion-height": 5, "fill-extrusion-base": 0,
                 "fill-extrusion-opacity": 0.95 } },

    { "id": "park-3d", "type": "fill-extrusion", "source": "omt", "source-layer": "park", "minzoom": 13,
      "paint": { "fill-extrusion-pattern": "tree", "fill-extrusion-height": 3, "fill-extrusion-base": 0,
                 "fill-extrusion-opacity": 0.95 } },

    { "id": "building-3d", "type": "fill-extrusion", "source": "omt", "source-layer": "building", "minzoom": 13,
      "paint": {
        "fill-extrusion-color": "#1C222B",
        "fill-extrusion-height": ["coalesce", ["get", "render_height"], 8],
        "fill-extrusion-base": ["coalesce", ["get", "render_min_height"], 0],
        "fill-extrusion-opacity": 0.92 } },

    { "id": "road-names", "type": "symbol", "source": "omt", "source-layer": "transportation_name", "minzoom": 15,
      "layout": { "symbol-placement": "line", "text-field": ["coalesce", ["get", "name:ru"], ["get", "name"]],
                  "text-font": ["Noto Sans Regular"], "text-size": 11 },
      "paint": { "text-color": "#6B737D", "text-halo-color": "#0A0C10", "text-halo-width": 1.5 } },

    { "id": "place-names", "type": "symbol", "source": "omt", "source-layer": "place",
      "filter": ["in", "class", "city", "town", "village", "suburb"],
      "layout": { "text-field": ["coalesce", ["get", "name:ru"], ["get", "name"]],
                  "text-font": ["Noto Sans Regular"], "text-size": 13 },
      "paint": { "text-color": "#8A9099", "text-halo-color": "#0A0C10", "text-halo-width": 1.5 } }
  ]
}
""".trimIndent()
}
