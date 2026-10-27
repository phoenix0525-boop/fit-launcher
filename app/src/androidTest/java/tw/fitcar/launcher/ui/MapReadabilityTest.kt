package tw.fitcar.launcher.ui

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.maplibre.android.MapLibre
import org.maplibre.android.style.layers.*
import org.maplibre.android.style.layers.PropertyFactory.*
import tw.fitcar.launcher.system.MapReadability

class MapReadabilityTest {
    @Test fun actualNativeLayersKeepShieldRulesAndGetChineseLabels() {
        val test = InstrumentationRegistry.getInstrumentation()
        test.runOnMainSync {
            MapLibre.getInstance(test.targetContext)
            val background = BackgroundLayer("background")
            val road = LineLayer("road_primary", "roads").withSourceLayer("transportation")
            val labels = SymbolLayer("road_label", "roads").withSourceLayer("transportation_name")
            labels.minZoom = 15f
            labels.setProperties(textField("{name:latin}"), textAllowOverlap(false))
            val shield = SymbolLayer("road_shield", "roads").withSourceLayer("transportation_name")
            shield.setProperties(textField("{ref}"))
            val poi = SymbolLayer("poi", "roads").withSourceLayer("poi")
            val count = MapReadability.applyLayers(listOf(background, road, labels, shield, poi), true, 1.25f)
            assertEquals(1, count)
            assertEquals(android.graphics.Color.parseColor("#243749"), background.backgroundColorAsInt)
            assertTrue(labels.textField.expression.toString().contains("name:zh"))
            assertTrue(labels.textSize.expression.toString().contains("26.25"))
            assertEquals(2f, labels.textHaloWidth.value!!, .001f)
            assertFalse(labels.textAllowOverlap.value!!)
            assertEquals(12f, labels.minZoom, .001f)
            assertEquals("{ref}", shield.textField.value)
            assertTrue(shield.textSize.value!! >= 18f)
            assertTrue(poi.minZoom >= 16f)
        }
    }
}
