# Editor de Notas para tablet — 1.7.0

Referencia de diseño y capacidades: [VectorCraft](https://github.com/storytold/vectorcraft), revisión `bd9b8a2db33bc42d3319a8b3666478f1b53acebc`.
La integración es nativa Kotlin/Compose/Canvas para Android y S Pen. No carga el editor de escritorio ni sustituye el motor de Discursos. Las operaciones geométricas se implementan con Android Path; no se ha copiado código Rust.

## Interfaz de estudio

La versión 1.7 sustituye las filas de botones y los diálogos de ajustes frecuentes por un espacio de trabajo inspirado en la composición de VectorCraft: fondo gris, papel separado del lienzo, barra de menús y controles, paleta de dos columnas por grupos y un inspector con Propiedades, Capas y Biblioteca. Los controles táctiles de herramientas miden 44 dp. En pantallas pequeñas el inspector se presenta como un panel superpuesto que se puede cerrar. Abrir el transcriptor deja más espacio para la hoja; después se pueden abrir las propiedades de una herramienta.

Al tocar un lápiz o la goma aparecen sus propiedades y una vista previa. Mantener pulsado un lápiz abre el grupo de herramientas. Las formas tienen un menú con iconos; la selección muestra una barra flotante de acciones. Transformar permite X/Y y tamaño numéricos; un cuadro de texto puede cambiar de ancho para redistribuir sus líneas sin cambiar el tamaño de letra, o escalar con proporciones conservadas.

La familia y el tamaño del texto están visibles en la barra superior. El inspector añade espaciado entre letras, color, resaltado y párrafo. Los cambios se dirigen al texto seleccionado, a lo que se va a escribir o al cuadro móvil activo. El interlineado se calcula por párrafo para que letras grandes no se solapen. Las fuentes Inter, Lora, Nunito, Roboto Mono, Patrick Hand y Caveat se incluyen en el APK con sus licencias OFL; funcionan sin conexión. Los metadatos se guardan con la nota, pasan por deshacer/rehacer y se renderizan en PDF/PNG. SVG incluye únicamente los archivos de fuentes usados por la nota.

Las plantillas se exploran en una galería con categorías, búsqueda, miniaturas del documento real y vista previa grande. Se selecciona un diseño y se aplica desde un botón explícito. La galería permite guardar la hoja actual, importar/exportar JSON y eliminar diseños propios. Conserva el contenido escrito al aplicar.

## Capacidades disponibles

| Área | Funciones |
| --- | --- |
| Espacio de trabajo | Biblioteca lateral expansible, herramientas arriba o a la izquierda, pantalla completa, zoom y desplazamiento, disposición del transcriptor persistente |
| Escritura | Fuentes sin conexión, tamaño y espaciado visibles, texto con formato, cuadros móviles, colores, resaltado, listas, alineación, citas interactivas |
| Tinta | Cuatro lápices con presión, presets, goma parcial o por objeto, S Pen, deshacer/rehacer compartido con texto |
| Dibujo vectorial | Línea, flecha, rectángulo, elipse, triángulo, estrella y curva Bézier cúbica; puntos/controladores editables |
| Apariencia | Relleno, degradado lineal, opacidad y borde discontinuo con vista previa y aplicación reversible |
| Combinación de formas | Unión, diferencia, intersección y exclusión; agujeros conservados como contornos de un solo objeto |
| Selección | Rectángulo, lazo libre, inspector de objetos, mover, escala, posición numérica, duplicar, eliminar, agrupar/desagrupar |
| Transformación | Giro y espejo de tinta, suavizado, alineación de seis tipos y distribución horizontal/vertical |
| Objetos | Bloquear, ocultar/mostrar y orden entre trazos o cuadros de texto |
| Imágenes | Importar desde el dispositivo; mover, escalar, duplicar, recortar sin perder el original, girar, opacidad y ubicación respecto a la tinta |
| Papel | Tamaño ajustable, renglones completos o por zona, cuadrícula, puntos, ajuste magnético de formas/controladores |
| Plantillas | Blanca, renglones, cuadrícula, puntos, Cornell, agenda, pentagramas; guardar la hoja como plantilla propia, importar/exportar plantillas JSON y reutilizarlas |
| Exportación | PDF paginado, PNG de toda la hoja, SVG con trazados/texto/imágenes; TXT de transcripción |
| Voz | Transcripción continua editable, original conservado, Vosk independiente, IA local Qwen y Biblia de Estudio |

Imágenes y propiedades vectoriales se guardan en el documento de la nota y viajan en la copia de seguridad. Las plantillas propias también se incluyen en la copia de seguridad desde Ajustes. La plantilla conserva una zona de escritura para el texto principal, sin incluir su contenido. Su aplicación conserva el contenido previo y añade su decoración con identificadores nuevos; repetirla añade una segunda decoración, que puede deshacerse.

Los ajustes de editor y voz se recuperan como últimos valores predeterminados del dispositivo. La posición del contenido dentro de una nota pertenece a su documento. La escala y el desplazamiento de vista pertenecen al espacio de trabajo.

## Límites de esta adaptación

No se declara equivalencia completa con VectorCraft. Todavía no incluye mallas de degradado, perspectiva, blends/repeticiones paramétricas, máscaras de recorte, efectos de apariencia múltiples, texto sobre trazado, importación `.vectorcraft`/`.ai` ni importación general SVG/PDF. El SVG exporta trazos con grosor base; la variación de presión y los fondos de resaltado de texto se conservan en PDF/PNG, no íntegramente en SVG.

El orden se mantiene entre elementos del mismo tipo. El texto queda encima de la tinta; las imágenes pueden ponerse detrás o delante de la tinta. La rotación de texto no está disponible. Las combinaciones usan los trazados originales para calcular Android Path; el resultado se conserva como contornos discretizados, no como curvas Bézier originales.

Las formas rellenas y los trazados compuestos se eliminan completos con la goma; la goma parcial recorta la tinta libre y las curvas sin relleno. El recorte de imágenes es reversible. Las imágenes se normalizan a un máximo de 1600 píxeles por lado para limitar memoria y almacenamiento; PNG limita su salida a ocho millones de píxeles. PDF conserva geometría vectorial y pagina hojas largas.

## Verificación

Pruebas de geometría, metadatos, compatibilidad con documentos anteriores, grupos, bloqueos, lazo, plantillas, imágenes, curvas y booleanos. Pruebas de Compose para propiedades al tocar, grupos al mantener pulsado, tipografía real, ancho de texto independiente del tamaño de letra, recuperación de ajustes, herramientas laterales, formas y galería de plantillas. Prueba Android de guardado de tipografía y aplicación de Cornell sin perder el contenido. Capturas de la composición real del editor. PDF se valida en Android real/emulado con PdfRenderer, porque el backend PDF nativo no está disponible en Robolectric.
