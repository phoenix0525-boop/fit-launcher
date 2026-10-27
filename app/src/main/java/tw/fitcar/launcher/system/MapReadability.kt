package tw.fitcar.launcher.system

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*

object MapReadabilityPolicy {
    const val NIGHT_BACKGROUND = "#243749"
    const val NIGHT_TEXT = "#E9F1F8"
    fun isRoadLabel(id: String, sourceLayer: String?) = sourceLayer == "transportation_name" &&
        !id.contains("shield", true) && !id.contains("ref", true) && !id.contains("oneway", true)
    fun fontScale(value: Float) = if (value.isFinite()) value.coerceIn(1f, 1.5f) else 1f
}

/** Applied once per loaded style. Preserve original filters, sources, credits and collision rules. */
object MapReadability {
    fun apply(style: Style, night: Boolean, fontScale: Float): Int = applyLayers(style.layers, night, fontScale)
    internal fun applyLayers(layers: List<Layer>, night: Boolean, fontScale: Float): Int {
        var labels = 0
        val scale = MapReadabilityPolicy.fontScale(fontScale)
        for (layer in layers) when (layer) {
            is BackgroundLayer -> if (night) layer.setProperties(backgroundColor(MapReadabilityPolicy.NIGHT_BACKGROUND))
            is FillLayer -> if (night) when (layer.sourceLayer) {
                "water" -> layer.setProperties(fillColor("#284E64"))
                "building" -> layer.setProperties(fillColor("#304353"), fillOpacity(.65f))
                "landcover", "landuse", "park" -> layer.setProperties(fillColor("#293F4B"))
            }
            is LineLayer -> if (night && layer.sourceLayer == "transportation") {
                val id = layer.id.lowercase()
                val color = when {
                    id.contains("casing") || id.contains("case") -> "#40566B"
                    id.contains("motorway") || id.contains("trunk") -> "#EDCA88"
                    id.contains("primary") -> "#E2EAF1"
                    id.contains("secondary") || id.contains("tertiary") -> "#C6D6E1"
                    else -> "#91A8BA"
                }
                layer.setProperties(lineColor(color))
            }
            is SymbolLayer -> if (MapReadabilityPolicy.isRoadLabel(layer.id, layer.sourceLayer)) {
                // Taiwan's local name is the final useful fallback when translated tags are absent.
                layer.setProperties(textField(coalesce(get("name:zh-Hant"), get("name:zh"), get("name"), get("name:latin"))),
                    textSize(interpolate(linear(), zoom(), stop(12, 18f * scale), stop(16, 21f * scale), stop(20, 25f * scale))),
                    textColor(if (night) MapReadabilityPolicy.NIGHT_TEXT else "#243749"),
                    textHaloColor(if (night) MapReadabilityPolicy.NIGHT_BACKGROUND else "#FFFFFF"),
                    textHaloWidth(if (night) 2f else 1.5f), textHaloBlur(.25f))
                layer.minZoom = minOf(layer.minZoom, 12f)
                labels++
            } else if (night && layer.sourceLayer == "poi") {
                // Keep POIs available at close range while prioritising road names during driving.
                layer.minZoom = maxOf(layer.minZoom, 16f)
            }
        }
        return labels
    }
}
