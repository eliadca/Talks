package com.eliadca.talks.core.track

import com.eliadca.talks.core.doc.silentRanges
import com.eliadca.talks.core.sample.SampleContent

object SampleScripts {
    val practice: ScriptIndex by lazy {
        val d = SampleContent.practice
        ScriptIndex.build(d.text, d.silentRanges(readHeadings = false))
    }

    /** A script with many repeated phrases, which is the hard case for position tracking. */
    val refrains: ScriptIndex by lazy { ScriptIndex.build(REFRAINS) }

    private const val REFRAINS = """No tengan miedo. Lo he dicho muchas veces y lo repito hoy: no tengan miedo de empezar.

Cuando era niño, mi abuelo me llevaba al río. Me decía: «Hijo, el agua no te hace daño si aprendes a respetarla». Yo tenía miedo, y él lo sabía. Pero él seguía diciéndome lo mismo: no tengas miedo, hijo, no tengas miedo.

Años después, cuando tuve que dejar mi casa para estudiar en la ciudad, recordé aquellas palabras. No tengas miedo. Llegué con una maleta pequeña y un sueño enorme. Y tuve miedo, claro que tuve miedo, pero seguí adelante.

Hoy les digo lo mismo que me dijo mi abuelo. Es posible. Es posible cambiar. Es posible empezar de nuevo. Es posible perdonar, y es posible ser perdonado.

¿Saben qué es lo más difícil? Lo más difícil no es el primer paso. Lo más difícil es el segundo, y el tercero, y el que viene después. Por eso les pido paciencia. Paciencia con ustedes y paciencia con los demás.

Un amigo me preguntó una vez: «¿Y si fracaso?». Le respondí: «Si fracasas, aprendes. Si aprendes, creces. Si creces, ya no eres la misma persona que empezó». Es posible fracasar y es posible levantarse. Lo importante es no quedarse en el suelo.

Hay tres cosas que quiero que recuerden hoy. Primero, que el miedo es normal. Segundo, que el miedo no manda. Tercero, que nadie camina solo. Repitan conmigo: el miedo es normal, el miedo no manda, nadie camina solo.

Así que hoy, cuando salgan de esta sala, no tengan miedo. No tengan miedo de pedir ayuda. No tengan miedo de ofrecerla. No tengan miedo de decir «te quiero», de decir «perdóname», de decir «empecemos otra vez».

Es posible. Lo he visto con mis propios ojos. Es posible, y empieza hoy. Muchas gracias.
"""
}
