### Task 2: Logout Modal

**Files:**
- Create: `app/src/main/java/com/frito/music/ui/components/YouTubeLogoutModal.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt`

**Interfaces:**
- Produces: `YouTubeLogoutModal` composable (bottom sheet)
- Consumes: `YouTubeLoginManager.getAccountName()`, `YouTubeLoginManager.getAccountEmail()`, `YouTubeLoginManager.logout()`

**Context:** This is the second task. When a logged-in user taps the user icon, show a modal with account info and logout button.

- [ ] **Step 1: Create YouTubeLogoutModal**

Create `YouTubeLogoutModal.kt` with:
- ModalBottomSheet
- Account info (name, email)
- Red "Cerrar Sesión" button
- "Cancelar" button

- [ ] **Step 2: Update StreamScreen to show logout modal**

Add state `showLogoutModal` and show modal when logged-in user taps icon.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/components/YouTubeLogoutModal.kt
git add app/src/main/java/com/frito/music/ui/screens/StreamScreen.kt
git commit -m "feat: add logout modal for YouTube account"
```
