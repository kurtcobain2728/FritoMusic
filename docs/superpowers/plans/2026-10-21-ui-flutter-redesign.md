# UI Modernization & Flutter Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the UI of FritoMusic Kotlin to achieve 100% aesthetic and fluid parity with the Flutter version (Glassmorphism, 3D orbital carousel, elastic floating navbar, glowing player controls, acoustic waveform, and haptic feedback) without touching or degrading the existing playback engine or core services.

**Architecture:** Maintain strict MVVM separation in Jetpack Compose. All audio playback ([MusicService](file:///c:/Users/Argenis/Documents/FritoMusic/app/src/main/java/com/frito/music/service/MusicService.kt), ExoPlayer, [PlayerViewModel](file:///c:/Users/Argenis/Documents/FritoMusic/app/src/main/java/com/frito/music/ui/viewmodels/PlayerViewModel.kt)) and data repositories remain unaltered. The visual presentation layer in `ui/` is updated with custom Canvas drawing, GPU-accelerated graphics layers, Spring animations, and glassmorphic styling tokens.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.02.00), Material 3, Coil Compose, AndroidX Media3 / ExoPlayer.

**Spec:** [docs/superpowers/specs/2026-10-21-ui-flutter-redesign-design.md](file:///c:/Users/Argenis/Documents/FritoMusic/docs/superpowers/specs/2026-10-21-ui-flutter-redesign-design.md)

## Global Constraints

- Min SDK: 26
- Compile / Target SDK: 34
- JVM Target: 21
- Package: `com.frito.music`
- Zero changes to `service/`, `data/`, `downloader/`, or `innertube/` business logic.
- All Compose code must compile with `./gradlew assembleDebug` without lint or build failures.

## Review Focus

- The floating bottom navigation bar and miniplayer must correctly respect Android window insets (`WindowInsets.navigationBars`) without clipping.
- The 3D orbital carousel must have zero frame drops during drag gestures on hardware GPU.
- Media playback controls (`playPause`, `seekTo`, `skipNext`, `skipPrevious`) in the redesigned player must keep exact 1-to-1 parity with current behavior.
- Gestures (vertical swipe-up to open player, swipe-down to dismiss, tap to seek) must not compete with system navigation bars or lyrics scroll.
- The waveform progress bar must update continuously and smoothly during playback without causing full-screen recompositions.

---

### Task 1: Theme & Animation Tokens, Glassmorphism Helpers & Haptic Utilities

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/theme/Animations.kt`
- Create: `app/src/main/java/com/frito/music/ui/theme/Glassmorphism.kt`
- Create: `app/src/main/java/com/frito/music/ui/theme/Haptics.kt`

**Interfaces:**
- Produces: `AppAnimations.springCurve`, `AppAnimations.cubicBezierSpec`, `AppAnimations.smoothSpring`
- Produces: `Modifier.glassCard(borderRadius, isDark, glowColor)`, `Modifier.glassBorder(...)`
- Produces: `Haptics.light(view: View)`, `Haptics.medium(view: View)`

- [ ] **Step 1: Expand `AppAnimations.kt` with Flutter-matching cubic bezier and spring specs**

Add the fluid cubic easing matching Flutter's `Cubic(0.16, 1.0, 0.3, 1.0)`:
```kotlin
val fluidCubicEasing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
fun <T> fluidCubicTween(durationMillis: Int = DURATION_PLAYER) =
    tween<T>(durationMillis, easing = fluidCubicEasing)
```

- [ ] **Step 2: Create `Glassmorphism.kt` with glass modifiers, soft drop-shadows and glowing borders**

Implement reusable Compose modifiers:
- `Modifier.glassSurface(...)`: Surface tint + subtle semi-transparent border (white/black depending on dark theme).
- `Modifier.glowShadow(color, alpha, blurRadius, spread)`: Soft glowing aura effect drawn via `drawBehind`.

- [ ] **Step 3: Create `Haptics.kt` utility**

Implement convenient helpers to trigger tactile haptic ticks across user interactions via `HapticFeedbackConstants.KEYBOARD_TAP` and `CLOCK_TICK`.

- [ ] **Step 4: Verify Compilation**

Run `./gradlew :app:compileDebugKotlin` and verify 0 errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/theme/
git commit -m "feat(ui): add glassmorphism tokens, cubic animation specs and haptic utilities"
```

---

### Task 2: Floating Glassmorphic Bottom Navigation Bar with Biphasic Elastic Indicator

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/components/BottomNavBar.kt`

**Interfaces:**
- Consumes: `LocalAppColors.current`, `Haptics`, `AppAnimations`
- Produces: `@Composable fun BottomNavBar(currentTab: String, onTabSelected: (String) -> Unit)`

- [ ] **Step 1: Replace Material 3 NavigationBar with floating glass container**

Structure the root layout as a floating capsule with `padding(horizontal = 20.dp, vertical = 12.dp)` and `RoundedCornerShape(30.dp)`.

- [ ] **Step 2: Implement the two-phase elastic indicator**

Recreate the indicator logic from `bottom_nav_bar.dart`:
- Calculate the target slot bounds based on selected tab index.
- Animate the indicator capsule using `animateFloatAsState` with a bouncy spring (`Spring.DampingRatioMediumBouncy`, `Spring.StiffnessMedium`).
- During animation, the indicator dilates (stretching across the gap) and then settles with overshoot.

- [ ] **Step 3: Add haptic feedback and icon micro-animations**

- Trigger `Haptics.light(view)` on tab tap.
- Animate icon scale and opacity on selection transition.

- [ ] **Step 4: Verify Compilation**

Run `./gradlew :app:compileDebugKotlin` and verify 0 errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/components/BottomNavBar.kt
git commit -m "feat(ui): implement floating glassmorphic BottomNavBar with biphasic elastic indicator"
```

---

### Task 3: Modern Floating MiniPlayer Card with Emerald Glow and Mini Equalizer

**Files:**
- Create: `app/src/main/java/com/frito/music/ui/components/MiniPlayer.kt`
- Modify: `app/src/main/java/com/frito/music/ui/screens/PlayerScreen.kt` (deprecate old MiniPlayer function)
- Modify: `app/src/main/java/com/frito/music/MainActivity.kt` (import new MiniPlayer)

**Interfaces:**
- Consumes: `PlayerViewModel` (`isPlaying`, `currentAudio`, `progress`, `durationMs`)
- Produces: `@Composable fun MiniPlayer(viewModel: PlayerViewModel, onClick: () -> Unit, onSwipeUp: () -> Unit)`

- [ ] **Step 1: Build floating card container in `ui/components/MiniPlayer.kt`**

- Container with `padding(horizontal = 14.dp, vertical = 6.dp)` and `RoundedCornerShape(24.dp)`.
- Dynamic border: Subtle glass border when paused, vivid radiant emerald green border (`#1DB954` at 40% alpha) when playing.
- Top pull indicator pill (30x3dp) to cue the swipe-up gesture.

- [ ] **Step 2: Implement album artwork with animated miniature equalizer overlay**

- Artwork thumbnail sized at 46x46dp with `RoundedCornerShape(14.dp)`.
- When `isPlaying == true`, display 3 animated vertical bars over the artwork undulating like an audio spectrum.

- [ ] **Step 3: Add controls, progress bar and gestures**

- Play/Pause button with tactile haptic response.
- Next button and dismiss button.
- Linear progress bar at the bottom with accent color.
- Vertical swipe-up gesture detection with haptic tick that calls `onSwipeUp()`.

- [ ] **Step 4: Verify Compilation**

Run `./gradlew :app:compileDebugKotlin` and verify 0 errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/components/MiniPlayer.kt app/src/main/java/com/frito/music/MainActivity.kt
git commit -m "feat(ui): create floating MiniPlayer card with emerald glow and mini equalizer"
```

---

### Task 4: Fullscreen Player Screen Modernization (3D Album Art, Glow Controls & Acoustic Waveform)

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/screens/PlayerScreen.kt`

**Interfaces:**
- Consumes: `PlayerViewModel`, `StreamViewModel`, `OnlineLibraryViewModel`
- Produces: `@Composable fun PlayerScreen(...)` with modernized UI matching `player_screen.dart`

- [ ] **Step 1: Modernize Header and Dismiss Handle**

- Add top drag indicator pill.
- Circle glass back button (`Icons.Rounded.KeyboardArrowDown`).
- Center live status pill: `"● REPRODUCIENDO"` with pulsing dot when active + album/streaming title.
- Circle glass right button for adding to playlist.

- [ ] **Step 2: Implement Album Artwork with Scale Punch & Emerald Aura**

- `RoundedCornerShape(28.dp)` with 1.2dp glass border.
- Dual shadow: deep black base shadow + radiant emerald green ambient glow shadow when playing.
- Animated scale: `1.0f` when playing, `0.92f` when paused, animated with `fluidCubicTween(400)`.

- [ ] **Step 3: Enhance Song Info and Artist Chip**

- Big bold title (22sp, weight 800).
- Pill-shaped chip for artist with chevron `>` that navigates to `onNavigateToArtist(id)` on tap.
- Circular glass download button (for online streams) and circular glass favorite heart button with active pink glow (`#FF4757`).

- [ ] **Step 4: Upgrade Waveform Progress Bar to Acoustic Frequency Spectrum**

- Port the 62-bar frequency envelope formula from `waveform_progress.dart`:
  - Sub-bass envelope (0-250Hz): `0.55 + 0.40 * sin(norm / 0.25 * PI/2)`
  - Mid-range (250Hz - 4kHz): `0.45 + 0.45 * sin(midNorm * PI)`
  - Highs (4kHz - 20kHz): `0.35 + 0.30 * (1.0 - highNorm)`
- Infinite continuous phase ticker on GPU canvas that flows smoothly without stutter during playback.
- Drag scrubbing with haptic feedback ticks on position change.
- Format timestamps and streaming/audio quality badge ("Streaming • Alta fidelidad").

- [ ] **Step 5: Implement Emerald Play/Pause Button and Glass Controls**

- Central Play/Pause button (76dp) with vibrant gradient (`#22C55E` to `#16A34A`) and 24dp blur drop shadow.
- Glass circular skip previous (52dp) and skip next (52dp) buttons.
- Shuffle and repeat buttons with active emerald circular aura.
- Bottom glass pill button `"Letra en tiempo real"` with lyrics icon and up chevron.

- [ ] **Step 6: Verify Compilation**

Run `./gradlew :app:compileDebugKotlin` and verify 0 errors.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/screens/PlayerScreen.kt
git commit -m "feat(ui): modernize PlayerScreen with glass controls, glowing art, and acoustic waveform"
```

---

### Task 5: 3D Orbital Turntable Carousel Component (`BenchoCarousel.kt`)

**Files:**
- Create: `app/src/main/java/com/frito/music/ui/components/BenchoCarousel.kt`

**Interfaces:**
- Produces: `@Composable fun BenchoCarousel(songs: List<SongItem>, onPlaySong: (SongItem, List<SongItem>) -> Unit, title: String? = null)`
- Produces: `@Composable fun BenchoArtistCarousel(artists: List<ArtistItem>, onArtistClick: (String) -> Unit, onSeeAllArtists: () -> Unit, title: String? = null)`

- [ ] **Step 1: Implement 3D Math and Orbital Perspective**

Recreate the orbital formulas from `bencho_carousel.dart`:
- Card dimensions: `width = 150.dp`, `height = 210.dp`, `orbit = 112.dp`, `lean = 26.dp`.
- Angle per item: `theta = (index - turn) * 0.72f`.
- 3D projections on `graphicsLayer`:
  - `translationX = sin(theta) * orbit`
  - `translationY = -(1f - cos(theta).coerceAtLeast(0f)) * lean`
  - `scaleX = scaleY = lerp(0.68f, 1.0f, depthFactor)`
  - `rotationY = -sin(theta) * 22f`
  - `rotationZ = cardAngle`

- [ ] **Step 2: Add Drag Gestures, Inertia and Idle Breathing Animation**

- Horizontal drag gesture with velocity calculation.
- Fling / Snap to closest card using `Animatable<Float, AnimationVector1D>` with `FastOutSlowInEasing`.
- Subtle idle floating / breathing animation in resting state.

- [ ] **Step 3: Implement Card Rendering with High-Resolution Artwork & Title Badges**

- Album art with rounded corners (20.dp), glass border and soft shadow.
- Play button overlay on center card.
- Artist variant with circular avatar art.

- [ ] **Step 4: Verify Compilation**

Run `./gradlew :app:compileDebugKotlin` and verify 0 errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/components/BenchoCarousel.kt
git commit -m "feat(ui): implement 3D orbital turntable carousel BenchoCarousel"
```

---

### Task 6: Stream Home Screen Integration with 3D Carousels

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/screens/StreamHomeScreen.kt`

**Interfaces:**
- Consumes: `BenchoCarousel`, `BenchoArtistCarousel`

- [ ] **Step 1: Replace flat "Vuelve a escuchar" with `BenchoCarousel`**

Integrate `BenchoCarousel` for the song shelves / recently played songs.

- [ ] **Step 2: Replace "Artistas para ti" with `BenchoArtistCarousel`**

Integrate `BenchoArtistCarousel` with "Ver todo" header button.

- [ ] **Step 3: Verify Compilation**

Run `./gradlew :app:compileDebugKotlin` and verify 0 errors.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/screens/StreamHomeScreen.kt
git commit -m "feat(ui): integrate 3D orbital carousels into StreamHomeScreen"
```

---

### Task 7: Synced Lyrics View Polish (Glow, Typography & Translation Menta)

**Files:**
- Modify: `app/src/main/java/com/frito/music/ui/components/SyncedLyricsView.kt`

**Interfaces:**
- Consumes: `LyricsUiState`, `LocalAppColors`

- [ ] **Step 1: Align active line typography and glow shadow**

- Active line: 26sp, FontWeight.ExtraBold (800), text shadow with accent aura.
- Inactive lines: 20sp, FontWeight.SemiBold (600), opacity 0.35f.
- Surrounding capsule container with subtle glass border (`Color.White.copy(alpha = 0.14f)`).

- [ ] **Step 2: Style translated lyrics sub-line in mint green**

- Set translated lyrics color to `#6EE7B7` (mint green) matching Flutter.

- [ ] **Step 3: Verify auto-resync timer (3.8s)**

- When user drags manually, pause auto-scroll; resume and re-center active line after 3.8s of inactivity.

- [ ] **Step 4: Verify Compilation**

Run `./gradlew :app:compileDebugKotlin` and verify 0 errors.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/frito/music/ui/components/SyncedLyricsView.kt
git commit -m "feat(ui): polish SyncedLyricsView with active glow, mint translation and glass highlight"
```

---

### Task 8: MainActivity Shell Integration, Morphing Sheet Transitions & Final Verification

**Files:**
- Modify: `app/src/main/java/com/frito/music/MainActivity.kt`

**Interfaces:**
- Consumes: All updated components (`BottomNavBar`, `MiniPlayer`, `PlayerScreen`)

- [ ] **Step 1: Update MiniPlayer and BottomNavBar presentation in MainActivity**

- Integrate the new floating `MiniPlayer` and `BottomNavBar`.
- Ensure `MiniPlayer` smoothly slides down and fades out when `showPlayerScreen == true`.

- [ ] **Step 2: Enhance Fullscreen Player Overlay Transition**

- Use `slideInVertically` with `AppAnimations.fluidCubicTween(380)` and morphing top corner radius.

- [ ] **Step 3: End-to-end Build Verification**

Run `./gradlew assembleDebug` in PowerShell and confirm BUILD SUCCESSFUL with 0 errors.

- [ ] **Step 4: Final Commit**

```bash
git add app/src/main/java/com/frito/music/MainActivity.kt
git commit -m "feat(ui): orchestrate floating glass shell, morphing transitions and finalize redesign"
```
