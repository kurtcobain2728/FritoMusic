# Diseño: Modernización Visual y Fluidez (Estilo Flutter / Glassmorphism) en FritoMusic Kotlin

**Fecha:** 2026-10-21  
**Estado:** Propuesto / En Planificación  
**Autor:** Antigravity & Argenis

---

## 1. Resumen Ejecutivo

Este diseño establece la especificación completa para transformar toda la interfaz visual de **FritoMusic Kotlin** ([c:\Users\Argenis\Documents\FritoMusic](file:///c:/Users/Argenis/Documents/FritoMusic)) para que alcance la misma estética prémium, fluidez, micro-interacciones y elegancia que se lograron en el prototipo de Flutter ([C:\Users\Argenis\Documents\fritomusicFlutter\musicfritoFlutter](file:///C:/Users/Argenis/Documents/fritomusicFlutter/musicfritoFlutter)).

### Regla Fundamental de Arquitectura (Cero Regresiones)
> [!IMPORTANT]
> **La capa de negocio, el motor de reproducción de audio y los servicios permanecen 100% intactos.**
> - `MusicService` (AndroidX Media3 / ExoPlayer) no se modifica.
> - Los ViewModels (`PlayerViewModel`, `HomeViewModel`, `StreamViewModel`, `OnlineLibraryViewModel`) mantienen sus contratos de datos y métodos inalterados.
> - La UI de Jetpack Compose simplemente se rediseña como una vista reactiva consumidora de los `StateFlow` existentes.

---

## 2. Elementos Clave del Rediseño

### 2.1. Tokens y Sistema de Diseño (`ui/theme/`)
- **Curvas de animación:** Adoptar la curva insignia de fluidez `CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)` para expansiones y sheets, junto con duraciones calibradas (`Fast = 180ms`, `Medium = 280ms`, `Slow = 420ms`, `Player = 380ms`).
- **Glassmorphism Helpers:** Componentes visuales con fondos translúcidos (`surface` con alfa 0.85-0.92), bordes sutiles de cristal (`Color.White.copy(alpha = 0.12f)` en modo oscuro) y sombras multicapa con difusión suave.
- **Micro-interacciones hápticas:** Helper centralizado para disparar vibración táctil ligera (`HapticFeedbackConstants.KEYBOARD_TAP` / `VIRTUAL_KEY`) en clics, cambios de pestaña, gestos de swipe y saltos de progreso.

### 2.2. Barra de Navegación Flotante (`BottomNavBar.kt`)
Inspirada en `GlassIconBar` (bencho.dev):
- **Estructura flotante:** Contenedor tipo píldora suspendido sobre el fondo (`margin horizontal = 20.dp, bottom = 12.dp`), con esquinas redondeadas de 30.dp y borde de cristal.
- **Indicador elástico bifásico:** Al cambiar de pestaña, la cápsula indicadora primero se estira (fase 1) hacia la pestaña de destino cubriendo la distancia, y luego contrae su cola (fase 2) asentándose con un rebote elástico suave (`Spring.DampingRatioMediumBouncy`).
- **Íconos modernos:** Escala sutil con animación al estar seleccionado y feedback háptico en cada toque.

### 2.3. MiniPlayer Flotante (`MiniPlayer.kt`)
- **Tarjeta suspendida:** Margen horizontal de 14.dp, bordes de 24.dp, sombra profunda y borde dinámico con **resplandor verde esmeralda** (`#1DB954`) cuando hay reproducción activa.
- **Carátula con ecualizador animado:** Esquinas redondeadas (14.dp) y superposición de 3 barras de ecualizador que oscilan en tiempo real durante la reproducción.
- **Manija superior:** Pequeño tirador central (30x3dp) que sugiere visualmente el gesto de arrastrar hacia arriba (*swipe up*) para abrir el reproductor completo.

### 2.4. Pantalla Completa del Reproductor (`PlayerScreen.kt`)
- **Apertura y morfología:** Animación vertical fluida que transiciona las esquinas superiores de 32.dp a 0.dp.
- **Cabecera moderna:** Tirador superior, botón circular de cristal para cerrar (`keyboard_arrow_down`), insignia dinámica con punto verde pulsante `"● REPRODUCIENDO"` y botón circular de cristal para agregar a listas.
- **Carátula con escala reactiva:** Radio de 28.dp, borde de cristal de 1.2dp, doble sombra (sombra de profundidad + resplandor verde de 36px) y animación de escala (`1.0` sonando, `0.92` pausado).
- **Píldora del Artista:** Chip redondeado con flecha sutil (`>`) que navega directamente al detalle del artista.
- **Waveform Progress Bar:** Espectrograma de 62 barras modelado acústicamente (graves, medios, agudos) con oscilador analógico continuo infinito en GPU, arrastre con feedback háptico e indicador de calidad ("Streaming • Alta fidelidad" o formato de archivo local).
- **Controles de Cristal y Botón Play Esmeralda:** Botón central de 76.dp con gradiente verde (`#22C55E` a `#16A34A`), sombra con resplandor difuso de 24.dp, y botones de anterior/siguiente en círculos de cristal translúcido.
- **Lanzador de Letras:** Botón inferior en cápsula de cristal `"Letra en tiempo real"` con ícono de letra y chevron hacia arriba.

### 2.5. Carrusel 3D Orbital Tipo Tornamesa (`BenchoCarousel.kt`)
- **Proyección orbital 3D:** Tarjetas calculadas en un cilindro espacial (`x = sin(theta) * orbit`, `y = -(1 - f) * lean`, rotación Y orientada a cámara y rotación Z orgánica individual).
- **Física de inercia y respiración:** Movimiento de flotación suave en reposo y arrastre con desaceleración natural.
- **Integración en Stream:** Usado en `StreamHomeScreen.kt` para las secciones "Vuelve a escuchar" y "Artistas para ti".

### 2.6. Letras Sincronizadas (`SyncedLyricsView.kt`)
- Resplandor difuso en el texto del verso activo.
- Tamaño ampliado (26sp, negrita 800) y cápsula translúcida alrededor de la línea activa.
- Sub-verso de traducción en verde menta (`#6EE7B7`).
- Temporizador inteligente de re-sincronización automática tras 3.8s de inactividad de scroll.

---

## 3. Matriz de Componentes y Archivos

| Componente | Archivo Kotlin | Responsabilidad |
|---|---|---|
| **Theme & Tokens** | `ui/theme/AppAnimations.kt`, `ui/theme/Glassmorphism.kt` | Curvas cúbicas, helpers de sombras, bordes de cristal y hápticos |
| **Bottom Bar** | `ui/components/BottomNavBar.kt` | Cápsula flotante con indicador bifásico elástico |
| **MiniPlayer** | `ui/components/MiniPlayer.kt` | Tarjeta flotante con glow verde y ecualizador animado |
| **Player Screen** | `ui/screens/PlayerScreen.kt` | Reproductor completo con carátula 3D, waveform acústico y controles de cristal |
| **Carrusel 3D** | `ui/components/BenchoCarousel.kt` | Carrusel cilíndrico orbital con perspectiva 3D |
| **Stream Home** | `ui/screens/StreamHomeScreen.kt` | Integración del carrusel en secciones destacadas |
| **Synced Lyrics** | `ui/components/SyncedLyricsView.kt` | Glow activo, tipografía escalada y traducción en verde menta |
| **Shell Root** | `MainActivity.kt` | Orquestación de capas flotantes y transiciones de pantalla |
