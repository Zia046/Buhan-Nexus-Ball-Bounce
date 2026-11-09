package com.burhannexus.ballbounce.engine

/** Minimal key/value storage so the score logic can be tested without Android. */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
}

class ScoreEntry(val score: Int, val timeMillis: Long)

/**
 * @param rank 1..3 if the score entered the TOP 3, otherwise 0
 * @param newBest true if the score beat the previous personal best
 */
class SubmitResult(val score: Int, val rank: Int, val newBest: Boolean, val previousBest: Int, val best: Int)

/** Personal best + TOP 3 leaderboard, persisted in a [KeyValueStore]. */
class ScoreBook(private val store: KeyValueStore) {

    fun best(): Int = store.getInt(KEY_BEST, 0)

    /** Highest first. At most 3 entries. Corrupt data is ignored. */
    fun top(): List<ScoreEntry> {
        val raw = store.getString(KEY_TOP) ?: return emptyList()
        val list = ArrayList<ScoreEntry>()
        for (part in raw.split(';')) {
            val bits = part.split(':')
            if (bits.size != 2) continue
            val s = bits[0].trim().toIntOrNull() ?: continue
            val t = bits[1].trim().toLongOrNull() ?: continue
            if (s <= 0) continue
            list.add(ScoreEntry(s, t))
        }
        // stable sort: equal scores keep the older entry first
        return list.sortedByDescending { it.score }.take(MAX_ENTRIES)
    }

    fun submit(score: Int, timeMillis: Long): SubmitResult {
        val previousBest = best()
        val newBest = score > previousBest
        if (newBest) store.putInt(KEY_BEST, score)

        var rank = 0
        if (score > 0) {
            val current = top().toMutableList()
            // A new score ties behind existing equal scores
            var index = current.size
            for (i in current.indices) {
                if (score > current[i].score) { index = i; break }
            }
            if (index < MAX_ENTRIES) {
                current.add(index, ScoreEntry(score, timeMillis))
                val trimmed = current.take(MAX_ENTRIES)
                store.putString(KEY_TOP, trimmed.joinToString(";") { "${it.score}:${it.timeMillis}" })
                rank = index + 1
            }
        }
        return SubmitResult(score, rank, newBest && score > 0, previousBest, maxOf(previousBest, score))
    }

    companion object {
        const val MAX_ENTRIES = 3
        const val KEY_BEST = "best_score"
        const val KEY_TOP = "top3_scores"
    }
}
