package com.eliadca.talks.core.text

import kotlin.math.pow

/**
 * Approximate unigram probability of a Spanish word in speech.
 *
 * The tracker uses this as "how surprising is it that the recogniser produced exactly this word":
 * hearing "de" says almost nothing about where the speaker is, hearing "esperanza" says a lot.
 * Only the order of the most frequent words matters, so a rank list with a Zipf curve is enough.
 */
object SpanishFrequency {

    private const val RANKED = """
de la que el en y a los se del las un por con no una su para es al lo como mas pero sus le ya o este si porque esta
entre cuando muy sin sobre tambien me hasta hay donde quien desde todo nos durante todos uno les ni contra otros ese
eso ante ellos e esto mi antes algunos que unos yo otro otras otra el tanto esa estos mucho quienes nada muchos cual
sea poco ella estar haber estas algunas algo nosotros mis tu te ti tus ellas nosotras vosotros os suyo suya suyos
nuestro nuestra nuestros nuestras esos esas estoy esta estamos estan es son era fue ser sido somos eres soy fueron eran
ha han he hemos habia haya hubo va van voy vamos iba ir hace hacer hecho hacen hago tiene tienen tengo tenemos tener
tenia puede pueden puedo podemos poder podria dice dijo decir digo dicen ver visto veo vemos dar da dio doy damos
saber sabe se sabemos sabia quiero quiere quieren querer queria gente vez veces anos ano tiempo dia dias cosa cosas
hombre hombres mujer mujeres vida casa mundo parte forma caso momento pais trabajo lugar manera nombre punto gobierno
grupo problema historia agua dios asi bien aqui ahora siempre despues hoy ayer nunca todavia solo mismo misma mismos
mismas cada tan tal menos mejor peor mayor menor primer primero segunda segundo gran grande nuevo nueva nuevos buen
bueno buena malo mala dos tres cuatro cinco seis siete ocho nueve diez cien mil mas aun cuanto cuanta cuantos
cuantas cual cuales quien donde cuando como porque pues entonces luego aunque mientras sino si ademas incluso
llegar llega llegado pasar pasa paso deber debe debemos debia poner pone puesto parecer parece quedar queda creer
creo cree creemos hablar habla hablo hablamos llevar lleva dejar deja seguir sigue encontrar encuentra llamar llama
venir viene vino vienen pensar pienso piensa salir sale volver vuelve tomar toma conocer conoce vivir vive vivimos
sentir siento siente tratar trata mirar mira contar cuenta empezar empieza esperar espera buscar busca existir existe
entrar entra trabajar trabaja escribir escribe perder pierde producir ocurrir entender entiendo entiende pedir pide
recibir recibe recordar recuerda terminar termina permitir permite aparecer aparece conseguir consigue comenzar
comienza servir sirve sacar saca necesitar necesita mantener mantiene resultar resulta leer lee caer cae cambiar
cambia presentar presenta crear crea abrir abre considerar considera oir oye acabar acaba convertir ganar gana
formar forma traer trae partir parte morir muere aceptar acepta realizar realiza suponer comprender comprende lograr
logra explicar explica preguntar pregunta tocar toca reconocer estudiar alcanzar alcanza nacer nace dirigir correr
utilizar usar usa pagar paga ayudar ayuda gustar gusta jugar juega escuchar escucha cumplir ofrecer descubrir
levantar intentar intenta
amor corazon vida verdad fe esperanza paz libertad familia hijos hijo hija padre madre hermanos hermano amigos amigo
ninos nino personas persona pueblo ciudad calle mano manos ojos cabeza palabra palabras ley justicia poder fuerza
camino futuro presente pasado mañana noche tarde mañana hora horas minuto minutos semana semanas mes meses
importante importantes necesario posible diferente diferentes mismo propio propia varios varias ciertos cierta
primera ultimo ultima largo corto alto bajo mucha muchas pocos pocas todas toda tanta tantos tantas ningun ninguna
ninguno alguien nadie cualquier cada demas mismo
eh mm este pues bueno osea vale ok
"""

    private val rank: Map<String, Int> by lazy {
        val m = HashMap<String, Int>()
        var r = 1
        for (w in RANKED.split(Regex("\\s+"))) {
            if (w.isEmpty()) continue
            val key = SpanishText.canonical(w)
            if (key !in m) m[key] = r
            r++
        }
        m
    }

    /** Frequency rank (1 = most frequent), or null when the word is not in the common list. */
    fun rankOf(canonicalWord: String): Int? = rank[canonicalWord]

    /** Words so common that matching them is nearly meaningless as evidence of position. */
    fun isStopword(canonicalWord: String): Boolean = (rank[canonicalWord] ?: Int.MAX_VALUE) <= 70

    /** Approximate probability that an arbitrary spoken word is exactly [canonicalWord]. */
    fun probability(canonicalWord: String): Double {
        val r = rank[canonicalWord]
        val effective = if (r != null) {
            r.toDouble()
        } else {
            // Unknown words are rarer the longer they are.
            val len = canonicalWord.length.coerceIn(3, 16)
            (1500.0 * 1.45.pow(len - 4)).coerceIn(900.0, 60000.0)
        }
        return (0.06 / effective.pow(0.95)).coerceAtLeast(1e-6)
    }
}
