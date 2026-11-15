package com.strmr.tv.library

import java.util.concurrent.Executors
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PostPlayDataContextTest {
    @Test fun `context changes remain observable by an open completion view`() = runTest {
        val before = ProfileDataContext.current; val values = mutableListOf<Long>()
        val observer = launch { ProfileDataContext.changes.take(2).toList(values) }; runCurrent()
        ProfileDataContext.invalidate(); runCurrent(); observer.join(); assertEquals(listOf(before,before+1),values)
    }
    @Test fun `concurrent context changes retain atomic epoch increments`() {
        val before = ProfileDataContext.current; val pool = Executors.newFixedThreadPool(4)
        try { val work = (1..4).map { pool.submit { repeat(50) { ProfileDataContext.invalidate() } } }; work.forEach { it.get() } } finally { pool.shutdown() }
        assertEquals(before+200,ProfileDataContext.current); assertEquals(ProfileDataContext.current,ProfileDataContext.changes.value)
    }
}
