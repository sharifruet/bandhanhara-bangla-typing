package com.bandhanhara.bangla

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.text.Normalizer
import java.util.Arrays
import java.util.concurrent.Executors
import kotlin.math.ln
import kotlin.math.pow

/** Marks the start of a sentence (or of the field) as the "previous word". */
const val SENTENCE_START = "<s>"

/** Characters that make up a Bangla word (letters, signs, hasanta, nukta, ZWJ/ZWNJ — not digits). */
fun isWordChar(c: Char): Boolean =
    c in 'ঀ'..'৥' || c == 'ৰ' || c == 'ৱ' || c in 'ৼ'..'৾' ||
        c == '‌' || c == '‍'

fun nfc(s: CharSequence): String = Normalizer.normalize(s, Normalizer.Form.NFC)

/**
 * What the user is typing, read from the text before the cursor.
 *
 * @property prev  the word before the one being typed (NFC), or [SENTENCE_START]
 * @property prev2 the word before [prev], or [SENTENCE_START]
 * @property prefix the partial word right before the cursor (NFC); empty after a space
 * @property rawPrefixLength length of the partial word as it sits in the editor, for replacing it
 */
data class TypingContext(val prev: String, val prev2: String, val prefix: String, val rawPrefixLength: Int)

fun parseContext(before: CharSequence): TypingContext {
    var i = before.length
    while (i > 0 && isWordChar(before[i - 1])) i--
    val rawPrefix = before.subSequence(i, before.length)

    // Walk back over up to two whole words, stopping at anything that isn't a space
    // (start of field, newline, । ? ! , …) — that marks a sentence start.
    val words = ArrayList<String>(2)
    var end = i
    while (words.size < 2) {
        var j = end
        while (j > 0 && (before[j - 1] == ' ' || before[j - 1] == ' ')) j--
        if (j == 0 || !isWordChar(before[j - 1])) break
        var k = j
        while (k > 0 && isWordChar(before[k - 1])) k--
        // A word cut off by the read window isn't trustworthy.
        if (k == 0 && before.length >= CONTEXT_WINDOW) break
        words += nfc(before.subSequence(k, j))
        end = k
    }
    return TypingContext(
        prev = words.getOrElse(0) { SENTENCE_START },
        prev2 = words.getOrElse(1) { SENTENCE_START },
        prefix = nfc(rawPrefix),
        rawPrefixLength = rawPrefix.length,
    )
}

/** How many characters before the cursor callers should pass to [parseContext]. */
const val CONTEXT_WINDOW = 96

/**
 * Word completion and next-word prediction.
 *
 * Built-in knowledge (assets, see tools/build_dictionary.py): ~30k words with frequencies and
 * ~22k word pairs. On top of that it learns from how the user actually types, on-device only
 * (files/user_history.tsv):
 *  - words, word pairs and three-word sequences the user types or picks,
 *  - with recency: counts halve every [HALF_LIFE_DAYS] days unless used again,
 *  - and rejections: a picked suggestion that is immediately deleted is demoted after that context.
 *
 * One instance per process ([get]) so the setup screen and the keyboard share the same memory.
 */
class Suggester private constructor(context: Context) {

    private class Dictionary(
        val sorted: Array<String>,              // lexicographic, for prefix ranges
        val freq: HashMap<String, Int>,
        val next: HashMap<String, List<Pair<String, Int>>>,
        val topStarters: List<String>,
    )

    /** A learned count that fades with time. */
    private class Stat(var count: Double, var day: Int)

    @Volatile private var dict: Dictionary? = null

    // User knowledge — touched only on the main thread.
    private val userWords = HashMap<String, Stat>()
    private val userPairs = HashMap<String, HashMap<String, Stat>>()    // prev → word
    private val userTriples = HashMap<String, HashMap<String, Stat>>()  // "prev2␁prev" → word
    private val rejected = HashMap<String, HashMap<String, Stat>>()     // prev → word
    private var loaded = false
    private var dirty = false
    private val readyCallbacks = ArrayList<() -> Unit>()

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val historyFile = File(appContext.filesDir, "user_history.tsv")
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "bandhanhara-dict").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { save() }

    init {
        io.execute {
            val d = loadDictionary()
            val h = loadHistory()
            main.post {
                dict = d
                mergeHistory(h)
                loaded = true
                readyCallbacks.forEach { it() }
                readyCallbacks.clear()
            }
        }
    }

    /** Run [cb] on the main thread once the dictionary and history are loaded (now, if they are). */
    fun whenReady(cb: () -> Unit) {
        if (loaded) cb() else readyCallbacks += cb
    }

    // ── Settings ───────────────────────────────────────────────────────────────

    var learningEnabled: Boolean
        get() = prefs.getBoolean(KEY_LEARN, true)
        set(value) = prefs.edit().putBoolean(KEY_LEARN, value).apply()

    /** Number of distinct words learned from the user. */
    val learnedWordCount: Int get() = userWords.count { weight(it.value) >= MIN_WEIGHT }

    /** The user's most-used words right now (recency-weighted). */
    fun topLearnedWords(limit: Int): List<String> =
        userWords.entries.sortedByDescending { weight(it.value) }.take(limit).map { it.key }

    /** Forget everything learned from the user. */
    fun clearLearned() {
        userWords.clear(); userPairs.clear(); userTriples.clear(); rejected.clear()
        dirty = false
        main.removeCallbacks(saveRunnable)
        io.execute { historyFile.delete() }
    }

    // ── Queries ────────────────────────────────────────────────────────────────

    /** Up to [limit] suggestions, best first. */
    fun suggest(ctx: TypingContext, limit: Int = 3): List<String> {
        val d = dict ?: return emptyList()
        return if (ctx.prefix.isEmpty()) predictNext(d, ctx, limit) else complete(d, ctx, limit)
    }

    private fun complete(d: Dictionary, ctx: TypingContext, limit: Int): List<String> {
        val prefix = ctx.prefix
        val scores = HashMap<String, Double>()

        var i = Arrays.binarySearch(d.sorted, prefix).let { if (it < 0) -it - 1 else it }
        while (i < d.sorted.size && d.sorted[i].startsWith(prefix)) {
            val w = d.sorted[i++]
            scores[w] = completionScore(d, ctx, w)
        }
        for (w in userWords.keys) if (w.startsWith(prefix) && w !in scores) scores[w] = completionScore(d, ctx, w)

        // Offer the word as typed only when nothing longer fits.
        return scores.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { it != prefix || scores.size == 1 }
            .take(limit)
    }

    private fun predictNext(d: Dictionary, ctx: TypingContext, limit: Int): List<String> {
        val scores = HashMap<String, Double>()
        d.next[ctx.prev]?.forEach { (w, c) -> scores[w] = ln(1.0 + c) }
        userPairs[ctx.prev]?.forEach { (w, s) -> scores[w] = (scores[w] ?: 0.0) + 3.0 * ln(1.0 + weight(s)) + 1.0 }
        userTriples[tripleKey(ctx.prev2, ctx.prev)]?.forEach { (w, s) ->
            scores[w] = (scores[w] ?: 0.0) + 5.0 * ln(1.0 + weight(s)) + 2.0
        }
        rejected[ctx.prev]?.forEach { (w, s) -> scores[w]?.let { scores[w] = it - REJECT_WEIGHT * ln(1.0 + weight(s)) } }

        val out = scores.entries.sortedByDescending { it.value }.map { it.key }.take(limit).toMutableList()
        // Not enough context: fall back to common sentence openers.
        for (w in d.topStarters) {
            if (out.size >= limit) break
            if (w !in out) out += w
        }
        return out
    }

    private fun completionScore(d: Dictionary, ctx: TypingContext, w: String): Double {
        val base = ln(1.0 + (d.freq[w] ?: 0))
        val user = userWords[w]?.let(::weight) ?: 0.0
        // Words the user has typed get a flat bonus so even a new name shows up once its prefix is typed.
        val userBonus = if (user >= MIN_WEIGHT) 5.0 + 2.5 * ln(1.0 + user) else 0.0
        val pair = (d.next[ctx.prev]?.firstOrNull { it.first == w }?.second ?: 0) +
            3.0 * (userPairs[ctx.prev]?.get(w)?.let(::weight) ?: 0.0)
        val triple = userTriples[tripleKey(ctx.prev2, ctx.prev)]?.get(w)?.let(::weight) ?: 0.0
        val reject = rejected[ctx.prev]?.get(w)?.let(::weight) ?: 0.0
        return base + userBonus + 4.0 * ln(1.0 + pair) + 5.0 * ln(1.0 + triple) - REJECT_WEIGHT * ln(1.0 + reject)
    }

    // ── Learning ───────────────────────────────────────────────────────────────

    /** Record that the user finished typing (or picked) [word] after [ctx]'s previous words. */
    fun learn(ctx: TypingContext, word: String) {
        if (!learningEnabled || !isLearnable(word)) return
        bump(userWords.getOrPut(word) { Stat(0.0, today()) })
        bump(userPairs.getOrPut(ctx.prev) { HashMap() }.getOrPut(word) { Stat(0.0, today()) })
        if (ctx.prev != SENTENCE_START) {
            bump(userTriples.getOrPut(tripleKey(ctx.prev2, ctx.prev)) { HashMap() }.getOrPut(word) { Stat(0.0, today()) })
        }
        // Using a word in a context forgives an earlier rejection there.
        rejected[ctx.prev]?.remove(word)
        markDirty()
    }

    /** The user picked [word] from the strip and then deleted into it: undo that learning and demote it here. */
    fun reject(ctx: TypingContext, word: String) {
        if (!learningEnabled) return
        drop(userWords, word)
        userPairs[ctx.prev]?.let { drop(it, word) }
        userTriples[tripleKey(ctx.prev2, ctx.prev)]?.let { drop(it, word) }
        bump(rejected.getOrPut(ctx.prev) { HashMap() }.getOrPut(word) { Stat(0.0, today()) })
        markDirty()
    }

    /** Write pending history now (keyboard hidden / service destroyed). */
    fun flush() {
        main.removeCallbacks(saveRunnable)
        save()
    }

    private fun isLearnable(word: String) =
        word.isNotEmpty() && word.length <= 24 && word.any { it in 'অ'..'হ' }

    private fun tripleKey(prev2: String, prev: String) = "$prev2\u0001$prev"

    private fun today() = (System.currentTimeMillis() / 86_400_000L).toInt()

    private fun weight(s: Stat): Double = s.count * 0.5.pow((today() - s.day).coerceAtLeast(0) / HALF_LIFE_DAYS)

    private fun bump(s: Stat) {
        s.count = weight(s) + 1.0
        s.day = today()
    }

    private fun drop(map: HashMap<String, Stat>, key: String) {
        val s = map[key] ?: return
        s.count = weight(s) - 1.0
        s.day = today()
        if (s.count < MIN_WEIGHT) map.remove(key)
    }

    private fun markDirty() {
        dirty = true
        main.removeCallbacks(saveRunnable)
        main.postDelayed(saveRunnable, SAVE_DELAY_MS)
    }

    // ── Persistence ────────────────────────────────────────────────────────────

    private fun save() {
        if (!dirty || !loaded) return
        dirty = false
        prune()
        val sb = StringBuilder("#v2\n")
        fun line(vararg f: Any) { f.joinTo(sb, "\t"); sb.append('\n') }
        fun fmt(s: Stat) = "%.3f".format(java.util.Locale.ROOT, s.count)
        for ((w, s) in userWords) line("u", w, fmt(s), s.day)
        for ((p, m) in userPairs) for ((w, s) in m) line("b", p, w, fmt(s), s.day)
        for ((k, m) in userTriples) {
            val (p2, p) = k.split('\u0001', limit = 2)
            for ((w, s) in m) line("t", p2, p, w, fmt(s), s.day)
        }
        for ((p, m) in rejected) for ((w, s) in m) line("r", p, w, fmt(s), s.day)
        val text = sb.toString()
        io.execute {
            try {
                val tmp = File(historyFile.parentFile, historyFile.name + ".tmp")
                tmp.writeText(text)
                tmp.renameTo(historyFile)
            } catch (_: Exception) {
                // Losing learned words is not worth crashing the keyboard over.
            }
        }
    }

    /** Forget what has faded away, then cap sizes by dropping the weakest entries. */
    private fun prune() {
        fun <K> HashMap<K, Stat>.fade() = entries.removeIf { weight(it.value) < MIN_WEIGHT }
        fun HashMap<String, HashMap<String, Stat>>.fadeNested() {
            values.forEach { it.fade() }
            entries.removeIf { it.value.isEmpty() }
        }
        userWords.fade()
        userPairs.fadeNested()
        userTriples.fadeNested()
        rejected.fadeNested()

        if (userWords.size > MAX_USER_WORDS) {
            val keep = userWords.entries.sortedByDescending { weight(it.value) }
                .take(MAX_USER_WORDS * 3 / 4).map { it.key }.toSet()
            userWords.keys.retainAll(keep)
        }
        for (m in listOf(userPairs, userTriples)) {
            if (m.values.sumOf { it.size } > MAX_USER_PAIRS) {
                m.values.forEach { inner -> inner.entries.removeIf { weight(it.value) < 1.5 } }
                m.entries.removeIf { it.value.isEmpty() }
            }
        }
    }

    private fun loadDictionary(): Dictionary {
        val freq = HashMap<String, Int>(40_000)
        appContext.assets.open("dict_words.tsv").bufferedReader().useLines { lines ->
            for (line in lines) {
                val tab = line.indexOf('\t')
                if (tab > 0) freq[line.substring(0, tab)] = line.substring(tab + 1).toIntOrNull() ?: 0
            }
        }
        val next = HashMap<String, MutableList<Pair<String, Int>>>(8_000)
        appContext.assets.open("dict_bigrams.tsv").bufferedReader().useLines { lines ->
            for (line in lines) {
                val p = line.split('\t')
                if (p.size == 3) next.getOrPut(p[0]) { ArrayList(4) } += p[1] to (p[2].toIntOrNull() ?: 0)
            }
        }
        val starters = next[SENTENCE_START]?.sortedByDescending { it.second }?.map { it.first }.orEmpty()
        val sorted = freq.keys.toTypedArray().also { it.sort() }
        return Dictionary(sorted, freq, HashMap(next), starters)
    }

    private class History(
        val words: Map<String, Stat>,
        val pairs: Map<String, Map<String, Stat>>,
        val triples: Map<String, Map<String, Stat>>,
        val rejected: Map<String, Map<String, Stat>>,
    )

    /** Reads both formats: v1 (`u w n`, `b p w n`) and v2 (adds a day column, `t` and `r` lines). */
    private fun loadHistory(): History {
        val words = HashMap<String, Stat>()
        val pairs = HashMap<String, HashMap<String, Stat>>()
        val triples = HashMap<String, HashMap<String, Stat>>()
        val rej = HashMap<String, HashMap<String, Stat>>()
        val now = today()
        fun stat(count: String, day: String?) = Stat(count.toDoubleOrNull() ?: 0.0, day?.toIntOrNull() ?: now)
        try {
            if (historyFile.exists()) historyFile.forEachLine { line ->
                val p = line.split('\t')
                when (p[0]) {
                    "u" -> if (p.size >= 3) words[p[1]] = stat(p[2], p.getOrNull(3))
                    "b" -> if (p.size >= 4) pairs.getOrPut(p[1]) { HashMap() }[p[2]] = stat(p[3], p.getOrNull(4))
                    "t" -> if (p.size >= 6) triples.getOrPut(tripleKey(p[1], p[2])) { HashMap() }[p[3]] = stat(p[4], p[5])
                    "r" -> if (p.size >= 5) rej.getOrPut(p[1]) { HashMap() }[p[2]] = stat(p[3], p[4])
                }
            }
        } catch (_: Exception) {
            // Corrupt or unreadable history: start fresh.
        }
        return History(words, pairs, triples, rej)
    }

    private fun mergeHistory(h: History) {
        fun merge(dst: HashMap<String, Stat>, src: Map<String, Stat>) {
            for ((k, s) in src) {
                val cur = dst[k]
                if (cur == null) dst[k] = s else { cur.count = weight(cur) + weight(s); cur.day = today() }
            }
        }
        fun mergeNested(dst: HashMap<String, HashMap<String, Stat>>, src: Map<String, Map<String, Stat>>) {
            for ((k, m) in src) merge(dst.getOrPut(k) { HashMap() }, m)
        }
        merge(userWords, h.words)
        mergeNested(userPairs, h.pairs)
        mergeNested(userTriples, h.triples)
        mergeNested(rejected, h.rejected)
    }

    companion object {
        private const val PREFS = "bandhanhara"
        private const val KEY_LEARN = "learn_from_typing"
        private const val SAVE_DELAY_MS = 4_000L
        private const val HALF_LIFE_DAYS = 45.0
        private const val MIN_WEIGHT = 0.2
        private const val REJECT_WEIGHT = 4.0
        private const val MAX_USER_WORDS = 20_000
        private const val MAX_USER_PAIRS = 40_000

        @Volatile private var instance: Suggester? = null

        fun get(context: Context): Suggester =
            instance ?: synchronized(this) { instance ?: Suggester(context).also { instance = it } }
    }
}
