package com.eliadca.talks.core.sample

import com.eliadca.talks.core.doc.Markup
import com.eliadca.talks.core.doc.RichDoc

/** Content the app creates on first launch so there is something to try straight away. */
object SampleContent {

    const val PRACTICE_TITLE = "Las pequeñas decisiones (discurso de práctica)"
    const val WELCOME_TITLE = "Bienvenido a Talks"

    val practice: RichDoc by lazy { Markup.parse(PRACTICE_MARKUP) }
    val welcome: RichDoc by lazy { Markup.parse(WELCOME_MARKUP) }

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
