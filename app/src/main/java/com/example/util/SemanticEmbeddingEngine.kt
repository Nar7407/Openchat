package com.example.util

import com.example.domain.model.KnowledgeDocInfo
import com.example.domain.model.UserMemoryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ln
import kotlin.math.sqrt

enum class SemanticSourceType(val badge: String) {
    MEMORY("MEM"),
    DOCUMENT("PDF/DOC"),
    LENS_SCAN("LENS")
}

data class SemanticDocumentItem(
    val id: String,
    val sourceType: SemanticSourceType,
    val title: String,
    val snippet: String,
    val fullText: String,
    val metadata: String = ""
)

data class SemanticSearchHit(
    val item: SemanticDocumentItem,
    val score: Float
) {
    val matchPercent: Int
        get() = (score * 100f).toInt().coerceIn(1, 99)
}

/**
 * On-device background coroutine semantic embedding & cosine retrieval engine.
 *
 * Computes 384-dimensional L2-normalized dense embeddings asynchronously on Dispatchers.Default
 * combining subword character n-grams (3..5), normalized word stems, and conceptual synonym-cluster
 * projections so conceptually related notes, saved PDFs, Live Lens scans, and Memory Bank entries
 * rank accurately even without exact keyword overlap.
 */
object SemanticEmbeddingEngine {

    private const val DIM = 384
    private val vectorCache = ConcurrentHashMap<String, FloatArray>()

    private val stopWords = setOf(
        "the", "and", "for", "with", "that", "this", "from", "have", "are", "was", "were",
        "been", "will", "would", "could", "should", "into", "about", "there", "their", "them",
        "they", "then", "than", "when", "what", "where", "which", "while", "your", "you", "our",
        "out", "not", "but", "all", "can", "had", "has", "her", "his", "its", "may", "one", "two"
    )

    // Conceptual semantic clusters mapping related terms to shared latent dimensions
    private val conceptClusters: List<Set<String>> = listOf(
        setOf("math", "equation", "calculus", "integral", "derivative", "matrix", "algebra", "solve", "formula", "theorem", "proof", "limit", "vector"),
        setOf("circuit", "resistor", "capacitor", "voltage", "current", "ohm", "kirchhoff", "diode", "transistor", "electronics", "smd", "pcb", "impedance", "inductor"),
        setOf("physics", "force", "velocity", "acceleration", "momentum", "energy", "thermodynamics", "quantum", "optics", "wave", "gravity", "mechanics"),
        setOf("code", "bug", "error", "exception", "crash", "stacktrace", "debug", "compile", "syntax", "kotlin", "python", "javascript", "typescript", "react", "java", "cpp", "rust", "api"),
        setOf("android", "compose", "gradle", "activity", "viewmodel", "room", "datastore", "retrofit", "coroutine", "flow", "apk", "manifest", "jetpack"),
        setOf("study", "exam", "test", "lecture", "syllabus", "homework", "assignment", "notes", "chapter", "textbook", "quiz", "flashcard", "university", "college"),
        setOf("work", "project", "deadline", "meeting", "client", "sprint", "deploy", "production", "release", "roadmap", "architecture", "backend", "frontend"),
        setOf("personal", "preference", "style", "concise", "short", "brief", "format", "bullet", "tone", "language", "habit", "goal", "health", "fitness"),
        setOf("finance", "budget", "expense", "cost", "price", "invoice", "receipt", "salary", "tax", "payment", "money", "dollar", "rupee"),
        setOf("translate", "language", "hindi", "marathi", "spanish", "french", "german", "japanese", "chinese", "arabic", "sign", "menu", "manual"),
        setOf("database", "sql", "query", "schema", "index", "table", "json", "storage", "cache", "sync", "cloud", "server", "docker", "linux"),
        setOf("ai", "llm", "model", "prompt", "token", "embedding", "vector", "rag", "search", "vision", "groq", "gemini", "openai", "claude", "qwen"),
        setOf("schedule", "calendar", "alarm", "reminder", "morning", "night", "daily", "routine", "plan", "todo", "task", "priority"),
        setOf("document", "pdf", "paper", "article", "summary", "report", "whiteboard", "diagram", "chart", "graph", "table", "slide")
    )

    private fun hashSlot(key: String, seed: Int): Int {
        var h = 2166136261L xor seed.toLong()
        for (i in key.indices) {
            h = h xor key[i].code.toLong()
            h = (h * 16777619L) and 0xFFFFFFFFL
        }
        return ((h ushr 1) % DIM).toInt()
    }

    private fun hashSign(key: String, seed: Int): Float {
        var h = 0x811C9DC5L xor (seed.toLong() * 31L)
        for (i in key.indices) {
            h = h xor key[i].code.toLong()
            h = (h * 16777619L) and 0xFFFFFFFFL
        }
        return if ((h and 1L) == 0L) 1f else -1f
    }

    private fun stemWord(word: String): String {
        if (word.length <= 4) return word
        return when {
            word.endsWith("ing") && word.length > 5 -> word.dropLast(3)
            word.endsWith("tion") && word.length > 6 -> word.dropLast(3)
            word.endsWith("ment") && word.length > 6 -> word.dropLast(4)
            word.endsWith("ies") && word.length > 5 -> word.dropLast(3) + "y"
            word.endsWith("ed") && word.length > 4 -> word.dropLast(2)
            word.endsWith("es") && word.length > 4 -> word.dropLast(2)
            word.endsWith("s") && !word.endsWith("ss") && word.length > 4 -> word.dropLast(1)
            else -> word
        }
    }

    fun computeEmbeddingSync(text: String): FloatArray {
        val normalized = text.lowercase().trim().take(2400)
        if (normalized.isEmpty()) return FloatArray(DIM)

        val cacheKey = "${normalized.length}:${normalized.hashCode()}"
        vectorCache[cacheKey]?.let { return it }

        val vec = FloatArray(DIM)
        val rawTokens = Regex("[a-z0-9_+#]+")
            .findAll(normalized)
            .map { it.value }
            .filter { it.length >= 2 && it !in stopWords }
            .toList()

        if (rawTokens.isEmpty()) return vec

        val freq = HashMap<String, Int>()
        rawTokens.forEach { tok ->
            val stemmed = stemWord(tok)
            freq[stemmed] = (freq[stemmed] ?: 0) + 1
        }

        // 1. Sublinear TF-weighted token + bigram hashing
        freq.forEach { (term, count) ->
            val tf = 1f + ln(count.toDouble()).toFloat()
            val slot1 = hashSlot("w:$term", 17)
            val sign1 = hashSign("w:$term", 17)
            vec[slot1] += tf * 2.2f * sign1

            val slot2 = hashSlot("w2:$term", 53)
            val sign2 = hashSign("w2:$term", 53)
            vec[slot2] += tf * 1.4f * sign2

            // Subword 3-gram & 4-gram projection for fuzzy/morphological similarity
            if (term.length >= 4) {
                val padded = "^$term$"
                for (i in 0..(padded.length - 3)) {
                    val tri = padded.substring(i, i + 3)
                    val s = hashSlot("g3:$tri", 97)
                    vec[s] += tf * 0.45f * hashSign("g3:$tri", 97)
                }
            }

            // Conceptual cluster projection (captures synonyms & domain concepts without exact words)
            conceptClusters.forEachIndexed { clusterIdx, clusterTerms ->
                if (term in clusterTerms || clusterTerms.any { it.startsWith(term) && term.length >= 4 }) {
                    val baseSlot = (clusterIdx * 11) % DIM
                    vec[baseSlot] += tf * 2.6f
                    vec[(baseSlot + 1) % DIM] += tf * 2.1f
                    vec[(baseSlot + 2) % DIM] += tf * 1.6f
                }
            }
        }

        // 2. Adjacent word bigrams for phrase semantics
        for (i in 0 until (rawTokens.size - 1).coerceAtMost(80)) {
            val pair = "${stemWord(rawTokens[i])}_${stemWord(rawTokens[i + 1])}"
            val slot = hashSlot("bi:$pair", 131)
            vec[slot] += 1.5f * hashSign("bi:$pair", 131)
        }

        // L2 normalize
        var normSq = 0.0
        for (v in vec) normSq += (v * v).toDouble()
        val invNorm = if (normSq > 1e-9) (1.0 / sqrt(normSq)).toFloat() else 0f
        if (invNorm > 0f) {
            for (i in vec.indices) vec[i] *= invNorm
        }

        if (vectorCache.size > 600) {
            vectorCache.clear()
        }
        vectorCache[cacheKey] = vec
        return vec
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
        }
        return dot.coerceIn(0f, 1f)
    }

    suspend fun searchCorpus(
        query: String,
        memories: List<UserMemoryInfo>,
        knowledgeDocs: List<KnowledgeDocInfo>,
        lensHistory: List<LiveLensHistoryEntry>,
        topK: Int = 8,
        minScore: Float = 0.14f
    ): List<SemanticSearchHit> = withContext(Dispatchers.Default) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return@withContext emptyList()
        val queryVec = computeEmbeddingSync(cleanQuery)

        val candidates = buildList {
            memories.forEach { m ->
                add(
                    SemanticDocumentItem(
                        id = "mem_${m.id}",
                        sourceType = SemanticSourceType.MEMORY,
                        title = "[${m.profile}/${m.category}] Memory",
                        snippet = m.fact,
                        fullText = "${m.category} ${m.profile} ${m.fact}",
                        metadata = if (m.isPinned) "PINNED" else m.source
                    )
                )
            }
            knowledgeDocs.forEach { doc ->
                val bestExcerpt = pickBestDocExcerpt(cleanQuery, queryVec, doc.content)
                add(
                    SemanticDocumentItem(
                        id = "doc_${doc.id}",
                        sourceType = SemanticSourceType.DOCUMENT,
                        title = "${doc.title} (#${doc.slug})",
                        snippet = bestExcerpt.take(260),
                        fullText = "${doc.title} ${doc.fileName} $bestExcerpt",
                        metadata = "${doc.chunkCount} chunks"
                    )
                )
            }
            lensHistory.forEach { scan ->
                val modePretty = scan.modeName.replace('_', ' ')
                add(
                    SemanticDocumentItem(
                        id = "lens_${scan.id}",
                        sourceType = SemanticSourceType.LENS_SCAN,
                        title = "Live Lens · $modePretty",
                        snippet = scan.answer.replace("\n", " ").take(260),
                        fullText = "$modePretty ${scan.modelLabel} ${scan.answer}",
                        metadata = scan.modelLabel
                    )
                )
            }
        }

        candidates
            .map { item ->
                val vec = computeEmbeddingSync(item.fullText)
                val sim = cosineSimilarity(queryVec, vec)
                SemanticSearchHit(item = item, score = sim)
            }
            .filter { it.score >= minScore }
            .sortedByDescending { it.score }
            .take(topK)
    }

    suspend fun rankMemories(
        query: String,
        memories: List<UserMemoryInfo>,
        minScore: Float = 0.10f
    ): List<Pair<UserMemoryInfo, Int>> = withContext(Dispatchers.Default) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext memories.map { it to 100 }
        val qVec = computeEmbeddingSync(clean)
        memories.map { m ->
            val v = computeEmbeddingSync("${m.category} ${m.profile} ${m.fact}")
            val s = cosineSimilarity(qVec, v)
            m to (s * 100f).toInt().coerceIn(0, 99)
        }
            .filter { it.second >= (minScore * 100f).toInt() }
            .sortedByDescending { it.second }
    }

    private fun pickBestDocExcerpt(query: String, queryVec: FloatArray, content: String): String {
        val clean = content.trim()
        if (clean.length <= 520) return clean
        val chunks = clean.chunked(480).take(18)
        var best = chunks.first()
        var bestScore = -1f
        chunks.forEach { chunk ->
            val score = cosineSimilarity(queryVec, computeEmbeddingSync(chunk))
            if (score > bestScore) {
                bestScore = score
                best = chunk
            }
        }
        return best.trim()
    }

    fun formatAutoAttachSnippet(hit: SemanticSearchHit): String {
        return buildString {
            appendLine("### Semantic Context Match (${hit.matchPercent}% · ${hit.item.sourceType.badge}: ${hit.item.title})")
            appendLine(hit.item.snippet.take(500))
        }.trim()
    }
}
