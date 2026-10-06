package com.eliadca.talks.core.sample

import com.eliadca.talks.core.doc.Markup
import com.eliadca.talks.core.doc.RichDoc

/** Content the app creates on first launch so there is something to try straight away. */
object SampleContent {

    const val PRACTICE_TITLE = "Las pequeñas decisiones (discurso de práctica)"
    const val WELCOME_TITLE = "Bienvenido a Talks"
    const val AGENT_TEMPLATE_TITLE = "Plantilla para tu agente de discursos"

    val practice: RichDoc by lazy { Markup.parse(PRACTICE_MARKUP) }
    val welcome: RichDoc by lazy { Markup.parse(WELCOME_MARKUP) }

    /** Kept exactly as typed, not parsed, so that copying the note copies the Markdown itself. */
    val agentTemplate: RichDoc by lazy { RichDoc(AGENT_TEMPLATE) }

    /**
     * A prompt for the AI assistant that writes the user's speeches: every bit of Markdown Talks
     * understands (see [Markup]), how to write for the voice tracker, and an example. A speech
     * written this way and pasted into Talks keeps all its formatting.
     */
    const val AGENT_TEMPLATE = """# Plantilla: discursos para Talks

Eres mi redactor de discursos. Leeré en voz alta lo que escribas con Talks, una app que me escucha, sigue por dónde voy y subraya lo que viene después. Entrégame siempre el discurso en el Markdown de Talks que te explico aquí, para que al pegarlo en la app conserve todo el formato.

## Cómo entregarlo
- Responde solo con el discurso: nada de comentarios antes ni después.
- Empieza con el título en una línea `# Título`; Talks lo usa como nombre del discurso.
- Cada párrafo va en una sola línea, sin saltos de línea dentro. Deja una línea en blanco entre párrafos.
- No lo metas en un bloque de código.
- Usa solo los formatos de la lista siguiente. Talks no muestra tablas, enlaces, imágenes, código, HTML ni emojis.

## Formatos que entiende Talks
- `# Título`, `## Sección` y `### Subsección`: la estructura del discurso (apertura, ideas, cierre). Los títulos no se leen en voz alta.
- `**negrita**` (o `__negrita__`): lo que quiero remarcar con la voz.
- `*cursiva*` (o `_cursiva_`): matices, títulos de obras o palabras en otro idioma.
- `++subrayado++`: una idea clave.
- `==resaltado==`: lo que no puedo olvidar; se ve en amarillo.
- `~~tachado~~`: una frase opcional que puedo saltarme si voy justo de tiempo.
- Los estilos se combinan: `***negrita y cursiva***`, `**==negrita resaltada==**`. Cada marca se abre y se cierra en la misma línea, pegada al texto: `**así**`, no `** así **`.
- `[Texto entre corchetes]`: una nota para mí que no se dice en voz alta, como `[Pausa]`, `[Mirar al público]`, `[Sonreír]` o `[Mostrar la diapositiva 3]`. Talks la muestra en otro color y no espera que la diga. Cada nota cabe en una línea. No uses corchetes para nada más.
- `- ` para viñetas (también `* ` o `+ `), `1. ` o `1) ` para listas numeradas, `- [ ] ` y `- [x] ` para listas de tareas. Para una sublista, pon dos espacios delante por cada nivel.
- `> ` delante de una cita o una frase célebre.
- `---` solo en una línea: un separador entre partes del discurso (se ve como · · ·).
- Una barra invertida delante de un símbolo lo escribe tal cual: `\*`, `\_`, `\#`.

## Escribir para la voz
- Talks compara lo que digo con el texto: escribe las palabras tal como las voy a pronunciar.
- Las cifras se entienden: 2024, 35.000, 3,5, 50 %, 1.º.
- Nada de abreviaturas ni símbolos que se lean distinto: «doctor», «etcétera», «dólares» en vez de Dr., etc. o el signo del dólar.
- Frases cortas y párrafos de dos a cuatro frases. Marca las pausas y los gestos con notas entre corchetes.
- Calcula unas 130 palabras por minuto, sin contar títulos ni notas.

## Ejemplo de entrega
```
# El valor de empezar
[Respirar. Mirar al público antes de empezar.]

## Apertura
Buenos días a todos. Gracias por regalarme lo más valioso que tienen: **su tiempo**.

¿Cuántas decisiones creen que han tomado hoy? [Pausa de tres segundos.]

## Primera idea: el primer paso
Hace años conocí a Marta. Un lunes cualquiera escribió *una sola página*. Mil quinientos días después, su libro tenía cuatrocientas páginas.

> El primer paso no te lleva a donde quieres ir, pero te saca de donde estás.

Tres preguntas para esta semana:
1. ¿Qué paso pequeño puedo dar hoy?
2. ¿Qué me lo impide?
  - Escríbanlo esta noche en un papel.
3. ¿Quién puede acompañarme?

---

## Cierre
==Empiecen hoy, aunque sea con una página.== ++Lo pequeño, repetido, se vuelve enorme.++ ~~Y si alguien lo duda, que le pregunte a Marta.~~

Muchas gracias. [Esperar los aplausos.]
```

## Tu encargo
En mi siguiente mensaje te diré el tema, el público, la duración y el tono. Si falta algo importante, pregúntame antes de escribir. Si no te digo la duración, apunta a unos cinco minutos. Cuando entregues el discurso, responde solo con él.
"""

    private const val WELCOME_MARKUP = """# Bienvenido a Talks
Talks es tu libreta de discursos. Escribe, da formato y, cuando llegue el momento, activa el **modo Talks**: la app te escucha y va subrayando lo próximo que tienes que decir.

## Cómo practicar ahora mismo
1. Abre el discurso «Las pequeñas decisiones (discurso de práctica)».
2. Pulsa el botón **Talks** de la barra superior.
3. Revisa la lista de comprobación (micrófono, idioma, sonido) y pulsa **Comenzar**.
4. Lee el texto en voz alta. Puedes improvisar, saltarte frases o repetirlas: la app te vuelve a encontrar.

## Si algo falla en el escenario
- **Mantén pulsada** cualquier palabra para colocar el seguimiento justo ahí.
- Un control remoto de presentaciones (o las teclas de volumen, si lo activas en Ajustes) avanza y retrocede de frase en frase.
- El botón de pausa deja de escuchar sin salir del discurso.
- Un toque simple muestra u oculta los controles.

## Escribir con formato
- Usa **negrita**, *cursiva*, ++subrayado++ y ~~tachado~~, colores y resaltado.
- Títulos, listas, listas de tareas y citas desde la barra de formato.
- Todo lo que escribas entre [corchetes] es una nota para ti: se ve, pero no se espera que lo digas. [Pausa. Mirar al público.]
- Atajos con teclado: Ctrl+B, Ctrl+I, Ctrl+U, Ctrl+Z, Ctrl+Y, Ctrl+F.

## Escribir con tu agente de IA
- La nota «Plantilla para tu agente de discursos» le explica a tu IA todo el formato que entiende Talks. Cópiala desde **Importar → Copiar plantilla para tu IA** y pégala en tu agente.
- Cuando te entregue un discurso, cópialo y pulsa **Importar → Pegar desde el portapapeles**: llega con sus títulos, negritas, listas y notas.
- También puedes importar archivos .md, .txt o de Word, compartir texto desde otra app hacia Talks o pegar Markdown dentro de un discurso.
- Para llevar un discurso a tu IA, usa **⋮ → Copiar como Markdown**.

## Tus discursos están a salvo
- Se guardan solos mientras escribes y guardan versiones anteriores.
- Desde Ajustes puedes hacer una copia de seguridad completa y restaurarla en otro dispositivo.
"""

    private const val PRACTICE_MARKUP = """# Las pequeñas decisiones
[Pausa. Mirar al público antes de empezar.]

## Apertura
Buenos días a todos. Gracias por estar aquí, y gracias por regalarme lo más valioso que tienen: su tiempo.

Quiero comenzar con una pregunta sencilla. ¿Cuántas decisiones creen que han tomado desde que se levantaron esta mañana? Piénsenlo un momento. [Esperar tres segundos.]

Los estudios dicen que tomamos más de treinta y cinco mil decisiones cada día. La inmensa mayoría son tan pequeñas que ni siquiera las notamos: qué ponernos, qué desayunar, por qué calle caminar, si saludamos o no a un desconocido.

Hoy quiero hablarles de esas decisiones diminutas, porque estoy convencido de que la vida no se transforma con grandes gestos, sino con pequeñas decisiones repetidas durante mucho tiempo.

## Primera idea: el valor del primer paso
Hace algunos años conocí a una mujer llamada Marta. Vivía en un pueblo pequeño y soñaba con escribir un libro. Durante veinte años repitió la misma frase: «Algún día lo haré».

Un lunes cualquiera, sin ceremonia y sin anunciarlo a nadie, decidió escribir una sola página. Solo una. Esa noche cerró el cuaderno y sintió algo que no había sentido en dos décadas: que por fin estaba en camino.

Ese libro tiene hoy cuatrocientas páginas. ¿Saben cuánto tardó en escribirlo? Mil quinientos días. Mil quinientos días de una página cada día.

Amigos, el primer paso casi nunca es heroico. Es pequeño, es incómodo y casi siempre parece insuficiente. Pero es el único que de verdad cambia la dirección.

## Segunda idea: la constancia
Ahora bien, empezar es solo la mitad del camino. La otra mitad se llama constancia, y la constancia no es emocionante.

Se parece mucho a regar una planta. Ningún día se ve crecer. Pasan semanas y parece que nada sucede. Y de pronto, una mañana, aparece una hoja nueva.

En el año 2024 un grupo de investigadores siguió a dos mil personas que querían cambiar un hábito. ¿Saben qué descubrieron? Que el noventa por ciento de quienes lo lograron no tenía más fuerza de voluntad que los demás. Lo que tenían era un plan pequeño y una rutina que podían cumplir incluso en sus peores días.

Por eso les propongo tres preguntas para esta semana:
- ¿Cuál es la decisión más pequeña que puedo tomar hoy para acercarme a lo que quiero?
- ¿Qué obstáculo concreto me detiene y cómo puedo hacerlo más pequeño todavía?
- ¿A quién puedo contarle mi plan para que me acompañe?

## Tercera idea: las personas que nos rodean
Y esto me lleva a la tercera idea, quizá la más importante. Nadie cambia solo.

Cuando Marta terminó su primer capítulo, se lo leyó a su vecina. Su vecina le dijo: «Sigue». Una palabra. Esa palabra la sostuvo durante cientos de mañanas difíciles.

Las pequeñas decisiones también se contagian. Una sonrisa a tiempo, una pregunta sincera, un «cuenta conmigo» dicho sin esperar nada a cambio. Cada uno de nosotros, queramos o no, es el ambiente en el que otros deciden cómo vivir.

Piensen en quién los ayudó a llegar hasta aquí. [Pausa larga.] Y ahora piensen en quién necesita hoy de ustedes una palabra como la de aquella vecina.

## Cierre
Voy a terminar como empecé, con una pregunta.

Dentro de cinco años, ¿qué les gustaría haber hecho? No me respondan ahora. Respóndanse esta noche, en silencio, y luego hagan lo más pequeño posible para empezar.

Porque no hace falta que sean valientes por un día. Hace falta que sean fieles todos los días.

Muchas gracias. Que tengan una semana llena de pequeñas decisiones buenas. [Sonreír y esperar el aplauso.]
"""
}
