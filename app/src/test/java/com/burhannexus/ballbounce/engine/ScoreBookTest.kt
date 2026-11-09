package com.burhannexus.ballbounce.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStore : KeyValueStore {
    val strings = HashMap<String, String>()
    val ints = HashMap<String, Int>()
    override fun getString(key: String): String? = strings[key]
    override fun putString(key: String, value: String) { strings[key] = value }
    override fun getInt(key: String, default: Int): Int = ints[key] ?: default
    override fun putInt(key: String, value: Int) { ints[key] = value }
}

class ScoreBookTest {

    @Test
    fun emptyLeaderboardAtFirst() {
        val book = ScoreBook(MemoryStore())
        assertTrue(book.top().isEmpty())
        assertEquals(0, book.best())
    }

    @Test
    fun keepsTopThreeSortedHighestFirst() {
        val book = ScoreBook(MemoryStore())
        book.submit(5, 1)
        book.submit(20, 2)
        book.submit(12, 3)
        book.submit(8, 4)
        book.submit(1, 5)
        assertEquals(listOf(20, 12, 8), book.top().map { it.score })
        assertEquals(20, book.best())
    }

    @Test
    fun reportsRankAndNewBest() {
        val book = ScoreBook(MemoryStore())
        val a = book.submit(10, 1)
        assertEquals(1, a.rank); assertTrue(a.newBest)
        val b = book.submit(7, 2)
        assertEquals(2, b.rank); assertFalse(b.newBest); assertEquals(10, b.best)
        val c = book.submit(15, 3)
        assertEquals(1, c.rank); assertTrue(c.newBest); assertEquals(10, c.previousBest)
        val d = book.submit(8, 4)
        assertEquals(3, d.rank)
        val e = book.submit(2, 5)
        assertEquals(0, e.rank); assertFalse(e.newBest)
    }

    @Test
    fun tieGoesBehindExistingScore() {
        val book = ScoreBook(MemoryStore())
        book.submit(10, 1)
        val r = book.submit(10, 2)
        assertEquals(2, r.rank)
        assertFalse(r.newBest)
        assertEquals(listOf(1L, 2L), book.top().map { it.timeMillis })
    }

    @Test
    fun zeroScoreIsNotListed() {
        val book = ScoreBook(MemoryStore())
        val r = book.submit(0, 1)
        assertEquals(0, r.rank)
        assertFalse(r.newBest)
        assertTrue(book.top().isEmpty())
    }

    @Test
    fun survivesRestartBecauseItReadsFromStore() {
        val store = MemoryStore()
        ScoreBook(store).apply { submit(9, 1); submit(30, 2) }
        val reopened = ScoreBook(store)
        assertEquals(listOf(30, 9), reopened.top().map { it.score })
        assertEquals(30, reopened.best())
    }

    @Test
    fun ignoresCorruptData() {
        val store = MemoryStore()
        store.putString(ScoreBook.KEY_TOP, "abc;12:99;;7:x;5:3:4;3:100")
        val book = ScoreBook(store)
        assertEquals(listOf(12, 3), book.top().map { it.score })
        book.submit(6, 200)
        assertEquals(listOf(12, 6, 3), book.top().map { it.score })
    }
}
