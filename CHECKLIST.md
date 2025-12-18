
## Test scenarios

| Scenario | Steps | Expected | Status |
| --- | --- | --- | --- |
| First run without permissions | Install/open app with both special permissions denied | Only the overlay and usage permission cards are shown; navigation is blocked until both are granted | Not run (emulator unavailable) |
| Ready state with permissions granted | Launch app with overlay + usage granted | No permission cards are visible on the main tab; status shows protection state only | Not run (emulator unavailable) |
| Overlay permission revoked at runtime | With protection enabled, revoke overlay permission in system settings then return | Overlay stops safely, a single overlay permission card appears, no crash | Not run (emulator unavailable) |
| Usage access revoked at runtime | With protection enabled, revoke usage access then return | Monitoring pauses without crashing; only the usage card appears until re-granted | Not run (emulator unavailable) |
| Re-grant permissions | After revocation, re-enable each permission | Permission cards disappear immediately; pending protection resumes once both are granted | Not run (emulator unavailable) |
| Protection toggle enable/disable | Toggle protection in Settings → Protection | Service/notification start and stop accordingly; preference persists across app restarts | Not run (emulator unavailable) |
| Rotation resilience | Rotate device on main, settings, and lock overlay screens | UI retains state (selected tab, search query, permission banners) without crashes | Not run (emulator unavailable) |
| Process death recovery | Force close/kill app with protection enabled then reopen | Toggle state restored from prefs; service restarts when permissions are still granted | Not run (emulator unavailable) |
| Locked-app launch on Android 10/11/12/13/14 | With an app locked and protection enabled, launch the locked app on each OS level | Lock overlay appears without ANRs or crashes; biometric/passcode unlock returns to the app | Not run (emulator unavailable) |
