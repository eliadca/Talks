package com.eliadca.talks.core.bible

import com.eliadca.talks.core.text.SpanishText
import java.io.DataInputStream
import kotlin.math.exp
import kotlin.math.sqrt

/** Quantized character-ngram classifier, about 59 KB, with no ML runtime or network access. */
internal class TinyBookModel private constructor(
    private val dimension: Int, private val labels: IntArray, private val scale: Float,
    private val biases: FloatArray, private val weights: ByteArray,
) {
    fun predict(text: String): Map<Int, Float> {
        val features = mutableMapOf<Int, Float>()
        val folded = SpanishText.foldForSearch(text).replace(" ", "")
        for ((prefix, value) in listOf("o" to folded, "p" to phonetic(folded))) {
            val padded = "^$value$"
            for (length in 2..4) for (start in 0..padded.length - length) {
                var hash = 2166136261L
                for (c in prefix + padded.substring(start, start + length)) hash = ((hash xor c.code.toLong()) * 16777619L) and 0xffffffffL
                val index = (hash % dimension).toInt()
                features[index] = (features[index] ?: 0f) + 1f
            }
        }
        val norm = sqrt(features.values.sumOf { (it * it).toDouble() }).toFloat().coerceAtLeast(1f)
        val logits = FloatArray(labels.size) { row ->
            biases[row] + features.entries.sumOf { (it.value / norm * weights[row * dimension + it.key] * scale).toDouble() }.toFloat()
        }
        val max = logits.max()
        val probabilities = logits.map { exp((it - max).toDouble()).toFloat() }
        val total = probabilities.sum()
        return labels.indices.associate { labels[it] to probabilities[it] / total }
    }

    companion object {
        val bundled: TinyBookModel? by lazy {
            TinyBookModel::class.java.getResourceAsStream("/bible/book-name-tiny.bin")?.use { input ->
                DataInputStream(input).use { stream ->
                    val header = ByteArray(4); stream.readFully(header); require(String(header, Charsets.US_ASCII) == "TBN1")
                    val dimension = stream.readUnsignedShort(); val count = stream.readUnsignedShort(); val scale = stream.readFloat()
                    require(dimension in 1..2048 && count in 1..70 && scale.isFinite() && scale > 0)
                    val labels = IntArray(count) { stream.readUnsignedByte() }
                    val biases = FloatArray(count) { stream.readFloat() }
                    val weights = ByteArray(dimension * count); stream.readFully(weights)
                    TinyBookModel(dimension, labels, scale, biases, weights)
                }
            }
        }

        fun phonetic(value: String): String = SpanishText.foldForSearch(value).replace(" ", "")
            .replace("ph", "f").replace("qu", "k").replace("ll", "y")
            .replace('v', 'b').replace('z', 's').replace(Regex("c(?=[ei])"), "s")
            .replace(Regex("g(?=[ei])"), "j").replace('c', 'k').replace("h", "").replace('y', 'i')
    }
}
