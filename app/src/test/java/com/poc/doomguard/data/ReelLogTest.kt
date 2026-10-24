package com.poc.doomguard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReelLogTest {
    @Before fun clear() = ReelLog.clear()

    @Test fun capDropsOldest() {
        val first = ReelLog.Record("first", null, null)
        ReelLog.add(first)
        repeat(ReelLog.CAP) { ReelLog.add(ReelLog.Record("r$it", null, null)) }
        assertEquals(ReelLog.CAP, ReelLog.size())
        val snap = ReelLog.snapshotAndClear()
        assertEquals("r0", snap.first().user)
        assertEquals("r${ReelLog.CAP - 1}", snap.last().user)
    }

    @Test fun snapshotClears() {
        val r = ReelLog.Record("a", "b", "c", ad = true)
        ReelLog.add(r)
        val snap = ReelLog.snapshotAndClear()
        assertEquals(1, snap.size)
        assertSame(r, snap[0])
        assertEquals(0, ReelLog.size())
        assertTrue(ReelLog.snapshotAndClear().isEmpty())
    }

    @Test fun clearEmpties() {
        ReelLog.add(ReelLog.Record(null, null, null))
        ReelLog.clear()
        assertEquals(0, ReelLog.size())
    }
}
