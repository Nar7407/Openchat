package com.example.domain.model

import kotlin.math.ln
import kotlin.math.sqrt

data class RagExcerpt(
    val docSlug: String,
    val docTitle: String,
    val chunkIndex: Int,
    val score: Double,
    val text: String
)

/**
 * Lightweight 100% client-side TF-IDF + cosine similarity retrieval engine
 * for local Knowledge Base documents referenced via `#doc-slug` or `#all`.
 */
object LocalRagEngine {

    private const val CHUNK_SIZE_CHARS = 750
    private const val CHUNK_OVERLAP_CHARS = 140

    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with",
        "by", "from", "as", "is", "was", "are", "were", "be", "been", "being", "have", "has",
        "had", "do", "does", "did", "will", "would", "could", "should", "may", "might", "can",
        "this", "that", "these", "those", "it", "its", "they", "them", "their", "we", "our",
        "you", "your", "i", "me", "my", "what", "which", "who", "how", "when", "where", "why",
        "not", "no", "yes", "if", "then", "else", "about", "into", "over", "after", "before"
    )

    private val hashDocRegex = Regex("""(?:^|\s)#([A-Za-z0-9][A-Za-z0-9_-]{1,40})""")

    fun slugify(raw: String): String {
        val cleaned = raw.lowercase()
            .substringBeforeLast('.')
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(36)
        return cleaned.ifBlank { "doc" }
    }

    fun extractReferencedSlugs(prompt: String): List<String> {
        return hashDocRegex.findAll(prompt)
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .toList()
    }

    fun splitIntoChunks(text: String): List<String> {
        val clean = text.replace("\r\n", "\n").trim()
        if (clean.isEmpty()) return emptyList()
        if (clean.length <= CHUNK_SIZE_CHARS) return listOf(clean)

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < clean.length) {
            var end = (start + CHUNK_SIZE_CHARS).coerceAtMost(clean.length)
            if (end < clean.length) {
                val paraBreak = clean.lastIndexOf("\n\n", end)
                val lineBreak = clean.lastIndexOf('\n', end)
                val spaceBreak = clean.lastIndexOf(' ', end)
                val preferred = when {
                    paraBreak > start + CHUNK_SIZE_CHARS / 2 -> paraBreak
                    lineBreak > start + CHUNK_SIZE_CHARS / 2 -> lineBreak
                    spaceBreak > start + CHUNK_SIZE_CHARS / 2 -> spaceBreak
                    else -> end
                }
                end = preferred
            }
            val slice = clean.substring(start, end).trim()
            if (slice.isNotEmpty()) chunks += slice
            if (end >= clean.length) break
            start = (end - CHUNK_OVERLAP_CHARS).coerceAtLeast(start + 1)
        }
        return chunks
    }

    fun retrieveTopExcerpts(
        query: String,
        docs: List<KnowledgeDocInfo>,
        referencedSlugs: List<String> = emptyList(),
        topK: Int = 4
    ): List<RagExcerpt> {
        if (docs.isEmpty()) return emptyList()
        val targetDocs = if (referencedSlugs.isEmpty() || "all" in referencedSlugs) {
            docs
        } else {
            docs.filter { doc ->
                referencedSlugs.any { slug ->
                    doc.slug.equals(slug, ignoreCase = true) ||
                        doc.slug.contains(slug, ignoreCase = true) ||
                        doc.title.contains(slug, ignoreCase = true)
                }
            }
        }
        if (targetDocs.isEmpty()) return emptyList()

        data class IndexedChunk(
            val doc: KnowledgeDocInfo,
            val chunkIndex: Int,
            val text: String,
            val tokens: List<String>,
            val termFreq: Map<String, Double>
        )

        val allChunks = mutableListOf<IndexedChunk>()
        targetDocs.forEach { doc ->
            splitIntoChunks(doc.content).forEachIndexed { idx, chunk ->
                val tokens = tokenize(chunk)
                if (tokens.isNotEmpty()) {
                    val counts = tokens.groupingBy { it }.eachCount()
                    val len = tokens.size.toDouble()
                    val tf = counts.mapValues { (_, c) -> c / len }
                    allChunks += IndexedChunk(doc, idx, chunk, tokens, tf)
                }
            }
        }
        if (allChunks.isEmpty()) return emptyList()

        val cleanedQuery = hashDocRegex.replace(query, " ").trim()
        val queryTokens = tokenize(cleanedQuery)
        if (queryTokens.isEmpty()) {
            // If user just asked to summarize `#doc` without specific keywords, return leading chunks
            return allChunks.take(topK).map {
                RagExcerpt(it.doc.slug, it.doc.title, it.chunkIndex, 1.0, it.text)
            }
        }

        val totalDocs = allChunks.size.toDouble()
        val docFreq = mutableMapOf<String, Int>()
        allChunks.forEach { chunk ->
            chunk.termFreq.keys.forEach { term ->
                docFreq[term] = (docFreq[term] ?: 0) + 1
            }
        }

        fun idf(term: String): Double {
            val df = docFreq[term] ?: 0
            return ln((totalDocs + 1.0) / (df + 1.0)) + 1.0
        }

        val qCounts = queryTokens.groupingBy { it }.eachCount()
        val qLen = queryTokens.size.toDouble()
        val qVec = qCounts.mapValues { (term, c) -> (c / qLen) * idf(term) }
        val qNorm = sqrt(qVec.values.sumOf { it * it })

        val scored = allChunks.map { chunk ->
            var dot = 0.0
            var cNormSq = 0.0
            chunk.termFreq.forEach { (term, tf) ->
                val w = tf * idf(term)
                cNormSq += w * w
                qVec[term]?.let { qw -> dot += qw * w }
            }
            val denom = qNorm * sqrt(cNormSq)
            val cosine = if (denom > 0.0) dot / denom else 0.0
            RagExcerpt(
                docSlug = chunk.doc.slug,
                docTitle = chunk.doc.title,
                chunkIndex = chunk.chunkIndex,
                score = cosine,
                text = chunk.text
            )
        }

        val sorted = scored.sortedByDescending { it.score }
        val positive = sorted.filter { it.score > 0.0 }.take(topK)
        return if (positive.isNotEmpty()) positive else sorted.take(topK.coerceAtMost(2))
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 && it !in stopWords }
    }
}
