import re

with open('app/src/main/java/com/frito/music/MainActivity.kt', 'r') as f:
    content = f.read()

# 1. Inject SharedPreferences and showOnboarding at the top of setContent
content = content.replace(
"""    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {""",
"""    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("FritoMusicPrefs", android.content.Context.MODE_PRIVATE)
        val hasCompletedOnboardingInitial = prefs.getBoolean("has_completed_onboarding", false)

        setContent {
            var showOnboarding by remember { mutableStateOf(!hasCompletedOnboardingInitial) }"""
)

# 2. Add Onboarding if-else logic inside FritoMusicTheme
content = content.replace(
"""            FritoMusicTheme(
                themeMode = themeMode,
                accentColorValue = accentColor,
                backgroundImageUri = backgroundImageUri,
                isDark = isDark
            ) {
                var currentTab by remember""",
"""            FritoMusicTheme(
                themeMode = themeMode,
                accentColorValue = accentColor,
                backgroundImageUri = backgroundImageUri,
                isDark = isDark
            ) {
                if (showOnboarding) {
                    com.frito.music.ui.screens.OnboardingScreen(
                        onFinish = {
                            prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                            showOnboarding = false
                        }
                    )
                } else {
                var currentTab by remember"""
)

# 3. Add gestor_descargas route
content = content.replace(
"""                                        "descargar" -> DownloadScreen(
                                            onBack = { currentSubScreen = null },
                                            onNavigateToArtist = { id ->
                                                selectedArtistId = id
                                                currentSubScreen = "artist_detail"
                                            },
                                            onNavigateToAlbum = { id ->
                                                selectedAlbumId = id
                                                currentSubScreen = "album_detail"
                                            },
                                            viewModel = downloadViewModel
                                        )""",
"""                                        "descargar" -> DownloadScreen(
                                            onBack = { currentSubScreen = null },
                                            onNavigateToArtist = { id ->
                                                selectedArtistId = id
                                                currentSubScreen = "artist_detail"
                                            },
                                            onNavigateToAlbum = { id ->
                                                selectedAlbumId = id
                                                currentSubScreen = "album_detail"
                                            },
                                            viewModel = downloadViewModel
                                        )
                                        "gestor_descargas" -> DownloadsManagerScreen(onBack = { currentSubScreen = null })"""
)

# 4. Add onNavigateToDownloadsManager to MoreScreen
content = content.replace(
"""                                                onNavigateToAppearance = { currentSubScreen = "apariencia" },
                                                onNavigateToDonations = { currentSubScreen = "donaciones" },
                                                onNavigateToDownload = { currentSubScreen = "descargar" },
                                                onNavigateToExtensions = { currentSubScreen = "extensiones" }""",
"""                                                onNavigateToAppearance = { currentSubScreen = "apariencia" },
                                                onNavigateToDonations = { currentSubScreen = "donaciones" },
                                                onNavigateToDownload = { currentSubScreen = "descargar" },
                                                onNavigateToDownloadsManager = { currentSubScreen = "gestor_descargas" },
                                                onNavigateToExtensions = { currentSubScreen = "extensiones" }"""
)

# 5. Add the closing brace for the `else` block we started above.
# We need to find the end of FritoMusicTheme block
# The easiest way is to find `} // End Scaffold` or similar, but looking at MainActivity.kt, the FritoMusicTheme block ends near the end of setContent.
# Wait, let's just use regex or find the exact block end.
content = content.replace(
"""                        }
                    }

                    // Player Overlay""",
"""                        }
                    }
                } // End if(showOnboarding) else

                    // Player Overlay"""
)

with open('app/src/main/java/com/frito/music/MainActivity.kt', 'w') as f:
    f.write(content)
print("Updated successfully")
