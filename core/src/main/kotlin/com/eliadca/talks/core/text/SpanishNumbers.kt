package com.eliadca.talks.core.text

/**
 * Converts integers to their Spanish spoken form, as folded words (no accents).
 *
 * A speech script may say "2024" while the recogniser hears "dos mil veinticuatro" (or the other
 * way round); expanding digits to words on both sides lets them align.
 */
object SpanishNumbers {

    private val units = arrayOf(
        "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
        "diez", "once", "doce", "trece", "catorce", "quince", "dieciseis", "diecisiete",
        "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidos", "veintitres",
        "veinticuatro", "veinticinco", "veintiseis", "veintisiete", "veintiocho", "veintinueve",
    )
    private val tens = arrayOf(
        "", "", "veinte", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa",
    )
    private val hundreds = arrayOf(
        "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
        "seiscientos", "setecientos", "ochocientos", "novecientos",
    )
    private val ordinals = arrayOf(
        "", "primero", "segundo", "tercero", "cuarto", "quinto", "sexto", "septimo", "octavo", "noveno", "decimo",
    )

    /** Spoken words for [n] (>= 0). Very large values fall back to digit-by-digit reading. */
    fun toWords(n: Long): List<String> {
        require(n >= 0) { "negative numbers are handled by the caller" }
        if (n == 0L) return listOf("cero")
        if (n >= 1_000_000_000_000_000L) return n.toString().map { units[it - '0'] }
        val out = ArrayList<String>()
        emit(n, out)
        return out
    }

    fun ordinal(n: Int): String? = if (n in 1..10) ordinals[n] else null

    private fun emit(n: Long, out: MutableList<String>) {
        when {
            n >= 1_000_000_000_000L -> {
                val q = n / 1_000_000_000_000L
                val r = n % 1_000_000_000_000L
                if (q == 1L) { out += "un"; out += "billon" } else { emit(q, out); out += "billones" }
                if (r > 0) emit(r, out)
            }
            n >= 1_000_000L -> {
                val q = n / 1_000_000L
                val r = n % 1_000_000L
                if (q == 1L) { out += "un"; out += "millon" } else { emit(q, out); out += "millones" }
                if (r > 0) emit(r, out)
            }
            n >= 1000L -> {
                val q = n / 1000L
                val r = n % 1000L
                if (q != 1L) emit(q, out)
                out += "mil"
                if (r > 0) emit(r, out)
            }
            n >= 100L -> {
                if (n == 100L) { out += "cien"; return }
                out += hundreds[(n / 100).toInt()]
                val r = (n % 100).toInt()
                if (r > 0) emit(r.toLong(), out)
            }
            n >= 30L -> {
                val t = (n / 10).toInt()
                val u = (n % 10).toInt()
                out += tens[t]
                if (u > 0) { out += "y"; out += units[u] }
            }
            else -> out += units[n.toInt()]
        }
    }
}
