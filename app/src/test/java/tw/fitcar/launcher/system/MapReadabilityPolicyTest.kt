package tw.fitcar.launcher.system
import org.junit.Assert.*
import org.junit.Test
import tw.fitcar.launcher.ui.Contrast
class MapReadabilityPolicyTest {
    @Test fun nightRoadsAndLabelsHaveVisibleContrast() {
        assertTrue(Contrast.ratio(0xFFE9F1F8, 0xFF243749) >= 7.0)
        assertTrue(Contrast.ratio(0xFFE2EAF1, 0xFF243749) >= 4.5)
        assertTrue(Contrast.ratio(0xFFEDCA88, 0xFF243749) >= 4.5)
        assertTrue(MapReadabilityPolicy.isRoadLabel("road_label", "transportation_name"))
        assertFalse(MapReadabilityPolicy.isRoadLabel("road_shield", "transportation_name"))
        assertFalse(MapReadabilityPolicy.isRoadLabel("highway_name_motorway", "transportation_name"))
        assertTrue(MapReadabilityPolicy.isRoadLabel("highway_name_other", "transportation_name"))
        assertFalse(MapReadabilityPolicy.isRoadLabel("poi_label", "poi"))
        assertEquals(1.25f, MapReadabilityPolicy.fontScale(1.25f))
        assertEquals(1f, MapReadabilityPolicy.fontScale(Float.NaN))
    }
}
