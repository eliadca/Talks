# Talks

**Libreta inteligente de discursos para tablets Samsung Galaxy Tab (y cualquier Android 8+).**
Escribe y da formato a tus discursos en español y, cuando llegue el momento, activa el **modo Talks**:
la app escucha lo que dices, entiende por dónde vas y **subraya lo próximo que tienes que decir**, para que puedas
mirar al auditorio y volver a la tablet sin perderte, aunque improvises o cambies palabras.

---

## Qué incluye

### Modo Talks (lo principal)
- **Sigue tu voz en español** y marca en pantalla la siguiente frase (subrayado grueso + resaltado + flecha en el margen).
  Lo ya dicho se atenúa y el texto se desplaza solo, manteniendo la línea actual a una altura fija con varias líneas por delante.
- **Tolera la improvisación**: si dices palabras que no están en el guion, el marcador se queda donde lo dejaste (estado «Improvisando»)
  y vuelve a engancharse cuando retomas el texto, aunque sea más adelante. Si te saltas párrafos o repites una frase, te encuentra de nuevo en pocas palabras.
- **Tolera los errores del reconocedor**: confusiones b/v, c/s/z, ll/y, h muda, plurales, números en cifras o en letras («2024» ↔ «dos mil veinticuatro»),
  palabras cortadas y fallos de reconocimiento.
- **Respaldo manual siempre disponible** (por si algo falla en el escenario): *mantén pulsada* cualquier palabra para colocar el marcador ahí;
  un **control remoto de presentaciones** (o las teclas de volumen, si lo activas) avanza/retrocede de frase en frase;
  un botón de pausa; y un aviso claro si el micrófono o el reconocedor fallan.
- **Indicador de estado siempre visible**: punto verde/ámbar/rojo + medidor del micrófono + cronómetro (y cuánto vas adelantado o atrasado respecto a tu tiempo objetivo).
- **Preparación**: comprueba micrófono y reconocedor, prueba de reconocimiento en vivo y elección del punto de inicio, antes de empezar.
- **Dos motores de voz**:
  - *Servicio de voz de Android (Google)*: no requiere configuración. Puede preferir el idioma descargado en el dispositivo (sin conexión).
  - *Sin conexión (Vosk)*: reconocimiento continuo **totalmente offline**, sin pitidos ni cortes entre frases. Descarga un modelo de ~40 MB una sola vez (o impórtalo de un `.zip`).
- Colores para escenario (Noche, Escenario amarillo, Día, Sepia), letra de 24 a 140 sp, imagen en espejo para teleprompter, pantalla siempre encendida, modo inmersivo.
- Al terminar, guarda tu **ritmo real** (palabras por minuto) para estimar con precisión cuánto dura cada discurso.

### Editor
- Negrita, cursiva, subrayado, tachado, color de texto, resaltado, tamaños, títulos, listas con viñetas / numeradas / de tareas (casillas tocables), citas,
  alineación, sangría, deshacer/rehacer, buscar y reemplazar (ignora mayúsculas y acentos), esquema por títulos.
- **Notas para ti**: lo que escribas entre `[corchetes]` (o marques con el botón «ojo») se ve atenuado y **no se espera que lo digas**: `[Pausa. Mirar al público.]`
- Atajos de teclado: `Ctrl+B`, `Ctrl+I`, `Ctrl+U`, `Ctrl+Z`, `Ctrl+Y`, `Ctrl+F`, `Tab`.
- Escritura a mano con el S Pen: el editor usa el motor de texto nativo de Android, así que «escribir a mano → texto» de One UI funciona.
- Se pega siempre como texto plano (para que lo que ves sea lo que se guarda).

### Biblioteca
- Diseño adaptable: **3 paneles en tablets grandes** (carpetas | lista | editor), 2 en medianas, 1 en teléfonos. Compatible con multiventana y DeX.
- Carpetas con color, fijados, etiquetas de color, orden por modificación/creación/título/duración, búsqueda en todos los discursos, papelera (30 días con deshacer).
- Tiempo estimado de cada discurso a tu ritmo real y **tiempo objetivo** con aviso si te pasas.
- **Historial de versiones** automático (cada 10 min de edición) y versiones con nombre que nunca se borran; restaurar guarda antes el texto actual.
- Exportar a **PDF** o compartir como texto; **importar** `.docx` (Word: títulos, negritas, listas…), `.md` y `.txt`;
  **copia de seguridad** completa en un solo archivo y restauración sin duplicados.

---

## Instalar en la tablet

El APK se compila automáticamente en GitHub Actions en cada cambio:

1. En GitHub, abre la pestaña **Actions** → el último *Android CI* en verde → sección **Artifacts** → descarga **`talks-apk`** (es un `.zip`).
2. En la tablet, descomprímelo (Mis archivos lo hace) e instala **`app-release.apk`** (permite «instalar apps desconocidas» cuando lo pida).
3. La primera vez que pulses **Comenzar** en el modo Talks, concede el permiso del micrófono.

> **Las actualizaciones se instalan encima y conservan tus discursos**, porque todos los APK se firman con la misma clave (`app/talks-sideload.jks`).
> Esa clave está en el repositorio a propósito, para uso personal. **No publiques la app en una tienda con ella** y mantén el repositorio privado.
> Aun así, haz una copia de seguridad (Ajustes → Copia de seguridad) antes de actualizar.

La variante `app-debug.apk` se instala junto a la normal (otro nombre de paquete) y sirve para pruebas.

## Primeros pasos

1. Abre **«Las pequeñas decisiones (discurso de práctica)»** (viene de ejemplo) y pulsa **Talks**.
2. Revisa la lista de preparación y pulsa **Probar micrófono**: di algo y comprueba que lo transcribe.
3. Pulsa **COMENZAR** y lee en voz alta. Prueba a improvisar una frase, saltarte un párrafo o repetir uno.
4. Para tu discurso real, **escríbelo o impórtalo**, ensáyalo en Talks varias veces (así aprende tu ritmo) y ajusta el tamaño de letra para tu distancia a la tablet.

## Consejos para un escenario real

- **Ensaya con el mismo micrófono y la misma tablet** que usarás. Si usas micrófono de solapa o auricular Bluetooth, pruébalo en la preparación.
- Con mala señal, usa el **motor sin conexión (Vosk)** o activa «Preferir reconocimiento sin conexión» y descarga el español en *Ajustes del sistema → Administración general → Idioma → Voz*.
- Deja la tablet **cargando** o con batería suficiente y desactiva el modo «No molestar» solo si necesitas que nada interrumpa; las notificaciones no bloquean la escucha.
- Si ves el punto **ámbar** («Improvisando» / «Buscando»), no pasa nada: sigue hablando; en cuanto retomes el texto se recoloca.
- Si el punto se pone **rojo**, la escucha se detuvo: sigue a mano (mantén pulsada una palabra) y pulsa **Reintentar** cuando puedas.
- Un toque simple muestra/oculta los controles; se ocultan solos a los 5 s. Salir pide confirmación para evitar toques accidentales.

## Cómo sabe por dónde vas

El guion se convierte en una secuencia de palabras normalizadas (sin acentos, con números expandidos y notas entre corchetes ignoradas).
Cada trozo de voz que entrega el reconocedor se compara con el guion mediante un **modelo oculto de Markov** de dos modos (*leyendo* / *improvisando*) sobre la posición
en el guion: cada palabra puede ser la siguiente, una posterior (te saltaste algo), una palabra extra o un salto a otro punto. La evidencia de cada coincidencia se pondera por
lo rara que es la palabra («esperanza» pesa mucho, «de» casi nada) y el resultado se filtra para que el marcador no salte por un reconocimiento dudoso.
Está en el módulo `core` (Kotlin puro) y se valida con un simulador de orador que mide la precisión frente a errores, improvisación, saltos y repeticiones.

## Para desarrolladores

```
core/   Kotlin puro: normalización del español, números, tokenizador, tracker de voz, modelo de documento, simulador de pruebas
app/    Android: Compose (biblioteca, ajustes, modo Talks), editor basado en EditText/spans, Room, DataStore, motores de voz
```

- Requisitos: JDK 17, Android SDK con la plataforma 36. `./gradlew :app:assembleRelease` genera el APK.
- Probar solo el motor (no necesita Android SDK): `./gradlew -PcoreOnly=true :core:test`.
- Pruebas: `:core:test` (JVM, incluye simulaciones estadísticas), `:app:testDebugUnitTest` (Robolectric: editor, lector, sesión, repositorio, copias, importación),
  `:app:connectedDebugAndroidTest` (emulador de tablet: abre la app real, escribe en el editor, recorre el modo Talks con un motor de voz simulado).
- CI: `.github/workflows/android.yml` compila, ejecuta todas las pruebas (incluido un emulador de tablet) y publica los APK.
- Hay un gancho de pruebas, `AppContainer.speechEngineFactory`, para inyectar un reconocedor falso.

### Qué está verificado y qué no
- **Verificado automáticamente**: el seguimiento de voz (precisión >99 % con ruido, recuperación media de 3 palabras tras un salto, ~0,5 ms por actualización
  con un guion de 10 000 palabras), el modelo de documento, el editor (formato, listas, deshacer, buscar), el lector, la sesión de Talks, la base de datos, las copias de seguridad,
  la importación de Word, y el recorrido completo de la interfaz en un emulador de tablet.
- **No verificable sin un dispositivo físico con micrófono**: la calidad del reconocimiento de voz del servicio de Android/Google en tu tablet y con tu micrófono,
  el comportamiento exacto del S Pen y de los mandos Bluetooth concretos. Por eso el modo Talks incluye prueba de micrófono, respaldo manual y aviso de errores.
  **Ensaya con tu equipo antes de un acto importante.**

## Privacidad

Los discursos se guardan solo en el dispositivo. El servicio de voz de Android puede enviar audio a Google para transcribirlo (como cualquier dictado del teléfono);
el motor sin conexión (Vosk) no envía nada. Talks solo usa Internet para descargar el modelo de voz opcional.
