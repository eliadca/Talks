package com.eliadca.talks.core.notes

import kotlinx.serialization.Serializable

/** A reviewed prefix never changes the recording; new recognition continues after it. */
@Serializable
data class TranscriptRevision(val text: String = "", val throughId: Long = 0, val assisted: Boolean = false) {
    fun render(chunks: List<Pair<Long, String>>): String = continuousText(
        (if (throughId > 0) listOf(text) else emptyList()) + chunks.filter { it.first > throughId }.map { it.second })
}

fun continuousText(parts: List<String>): String = parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")

/** Reject dangerous rewrites, while still allowing punctuation, spelling and nearby sound errors. */
object CorrectionReview {
    fun clean(value: String): String = value.trim().removePrefix("```text").removePrefix("```").removeSuffix("```").trim()
        .removeSurrounding("\"").trim()
    fun concern(original: String, corrected: String): String? {
        if (corrected.isBlank()) return "La IA no produjo una propuesta."
        val originalNumbers = Regex("\\d+").findAll(original).map { it.value }.toList()
        val correctedNumbers = Regex("\\d+").findAll(corrected).map { it.value }.toList()
        if (originalNumbers != correctedNumbers) return "La propuesta cambia números o citas. Revisa el original."
        val originalWords = Regex("[\\p{L}\\p{N}]+").findAll(original).map { it.value.lowercase() }.toList()
        val words = Regex("[\\p{L}\\p{N}]+").findAll(corrected).map { it.value.lowercase() }.toList()
        if (words.size > originalWords.size * 1.35 + 3 || words.size < originalWords.size * .65) return "La propuesta cambia demasiado el contenido."
        if (words.any { it in listOf("corregido", "corregida", "transcripción", "explicación") } && words.take(4) != originalWords.take(4))
            return "La propuesta parece contener una explicación."
        // LCS permits small substitutions but rejects a fluent, unrelated paraphrase.
        val a = originalWords.take(300); val b = words.take(400); val row = IntArray(b.size + 1)
        for (word in a) { var diagonal = 0; for (j in b.indices) { val old = row[j+1]; row[j+1] = if (word == b[j]) diagonal+1 else maxOf(row[j+1],row[j]); diagonal=old } }
        if (a.size > 8 && row.last() < a.size * .55) return "La propuesta se aleja del original."
        return null
    }
}
