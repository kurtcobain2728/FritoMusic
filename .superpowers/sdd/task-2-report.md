### Task 2: Logout Modal - COMPLETED

**Status:** Done

**Changes Made:**

1. **Created** `app/src/main/java/com/frito/music/ui/components/YouTubeLogoutModal.kt`
   - ModalBottomSheet with dark theme (Color 0xFF222222)
   - Account icon with green background (Color 0xFF1DB954)
   - Account name display with fallback "Usuario de YouTube"
   - Account email display
   - Red "Cerrar Sesión" button (Color 0xFFE53935) with logout icon
   - Gray "Cancelar" button (Color 0xFF333333)
   - Uses AutoMirrored.Filled.Logout icon (non-deprecated)

2. **Modified** `app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt`
   - Added import for YouTubeLogoutModal
   - Added `showLogoutModal` state variable
   - Updated user icon click behavior: logged-in users see modal, non-logged-in users navigate to login
   - Added YouTubeLogoutModal composable at end of StreamScreen

**Verification:**
- Compilation: `./gradlew :app:compileDebugKotlin` - BUILD SUCCESSFUL (no warnings)

**Commit:** `ae399a8` - "feat: add logout modal for YouTube account"
