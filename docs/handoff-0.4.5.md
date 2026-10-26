# 車機桌面 0.4.5 (12)

Based on claude/agama-features 27151b6132d0371eee35cf7556bcca8db30cb89f (0.4.4).

## Implemented
- Display name 車機桌面; package ID/signing policy unchanged for upgrade continuity. About contains only 個人化啟動器, version and changes.
- Icon-only shortcut tiles with accessible app labels; tap and long press preserved.
- Media volume button shows actual Android stream index, dialog offers slider and +/-; read back changes and report restrictions. Vendor amplifier needs vehicle verification.
- Map freshness uses the real monotonic clock at composition, not a timer tick older than an incoming GPS sample (previous logic could flicker between fresh and waiting).
- Hold last marker up to 60 seconds during short outages, explicitly label stale; 10-second current-fix diagnostic remains unchanged. No movement prediction. Smooth camera animation, continuous speed zoom targets 17 at stop to 14 at 120 km/h with bounded transitions. Manual browse suspends following; recenter resumes.
- Chinese address kept during refresh/failure, labeled 上次位置 beyond 300m or 2 minutes or when GPS is stale. No coordinate fallback. This is reverse geocoding, not highway map matching; accurate mainline/ramp/road-section identification remains unresolved. Async Android13 geocoder with 8-second timeout, slower retry on failures.
- Correct headlight test wording and key-log interpretation. User reports factory launcher keys work and headlights cause no brightness change. These hardware integration issues are NOT claimed fixed.

## Vehicle acceptance still needed
1. Drive city/highway/tunnel, check no repeated waiting/marker disappearance on fresh 1Hz fixes, stale marker is labeled and removed after 60 sec; confirm address changes honestly.
2. Check automatic zoom, manual map browsing, recenter, GPS disable/permission revoke and returning from external nav.
3. Compare media-volume index and audible level, then steering-wheel change synchronizes the displayed index. Check max/min and fixed-volume behavior.
4. Icon-only shortcuts launch/long-press correctly at font scale125%; About and app/system label are 車機桌面.
5. Record which steering key fails and received key codes; compare brightness with schedule disabled and factory launcher. No vendor broadcast names guessed.

## Next requested capability
User asks system split-screen shortcut because manual Android13/factory split works. Candidate: optional AccessibilityService GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN only if exposed in getSystemActions, otherwise RECENTS and manual choice. Requires explicit on-device service enablement. Not implemented in this change; cannot promise direct split or hardware-key mapping. Keep external navigation fullscreen by default.
