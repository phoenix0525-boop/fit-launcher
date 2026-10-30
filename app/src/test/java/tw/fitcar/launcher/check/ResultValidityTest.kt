package tw.fitcar.launcher.check
import org.junit.Assert.*
import org.junit.Test

class ResultValidityTest {
    @Test fun oldVersionsAndLegacyResultsNeverCountAsCurrent() {
        assertFalse(ResultValidity.matches("0.2.1 (3)", "FIT=true", "0.3.0 (4)", "FIT=true"))
        assertFalse(ResultValidity.matches("", "", "0.3.0 (4)", "FIT=true"))
    }
    @Test fun reverseResultNeedsSameDefaultHomeContext() {
        assertFalse(ResultValidity.matches("v4", "FIT=false", "v4", "FIT=true"))
        assertTrue(ResultValidity.matches("v4", "FIT=true", "v4", "FIT=true"))
    }
    @Test fun sameBootIsOnlyObservation() {
        assertTrue(BootComparison.describe(10, 10, 1000, 9000).contains("無法單憑此判定"))
        assertTrue(BootComparison.describe(10, 11, 1000, 9000).contains("重新開機"))
        assertTrue(BootComparison.describe(-1, -1, 1000, 9000).contains("不足以確認"))
    }

    private val kkbox = "預設桌面=tw.fitcar.launcher; FIT預設=true; 播放器=kkbox; 模式=FOLLOW; 控制來源=kkbox; 導航=kingwaytek; 導航模式=SPLIT_FIRST; 字體=1.25"
    private val ytm = kkbox.replace("播放器=kkbox", "播放器=ytmusic").replace("控制來源=kkbox", "控制來源=ytmusic")

    @Test fun switchingPlayerKeepsVerifiedBacklightAndNavigation() {
        assertTrue(ResultValidity.matches("v1", kkbox, "v1", ytm, "brightness_test"))
        assertTrue(ResultValidity.matches("v1", kkbox, "v1", ytm, "m_headlight"))
        assertTrue(ResultValidity.matches("v1", kkbox, "v1", ytm, "m_google_hint"))
        assertTrue(ResultValidity.matches("v1", kkbox, "v1", ytm, "m_reverse"))
    }
    @Test fun switchingPlayerInvalidatesPlayerAndAudioChecks() {
        assertFalse(ResultValidity.matches("v1", kkbox, "v1", ytm, "m_player_kkbox"))
        assertFalse(ResultValidity.matches("v1", kkbox, "v1", ytm, "audio_probe_KEYS"))
        assertFalse(ResultValidity.matches("v1", kkbox, "v1", ytm, "m_wheel"))
    }
    @Test fun relevantFieldStillChecked() {
        val notHome = kkbox.replace("FIT預設=true", "FIT預設=false")
        assertFalse(ResultValidity.matches("v1", kkbox, "v1", notHome, "m_reverse"))
        assertFalse(ResultValidity.matches("v1", kkbox, "v1", notHome, "gesture_two_up"))
        assertTrue(ResultValidity.matches("v1", kkbox, "v1", notHome, "brightness_test"))
    }
    @Test fun versionAlwaysMattersAndUnknownIdsStayConservative() {
        assertFalse(ResultValidity.matches("v1", kkbox, "v2", kkbox, "brightness_test"))
        assertFalse(ResultValidity.matches("v1", kkbox, "v1", ytm, "some_future_check"))
        assertTrue(ResultValidity.matches("v1", kkbox, "v1", kkbox, "some_future_check"))
    }
    @Test fun legacyRecordMissingFieldIsNotTreatedAsMatch() {
        assertFalse(ResultValidity.matches("v1", "FIT預設=true", "v1", kkbox, "m_player_kkbox"))
        assertTrue(ResultValidity.matches("v1", "FIT預設=true", "v1", kkbox, "brightness_test"))
    }
}
