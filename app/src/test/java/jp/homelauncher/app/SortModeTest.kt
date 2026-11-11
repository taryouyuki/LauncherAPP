package jp.homelauncher.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SortModeTest {
    @Test fun sortModesArePersistable() {
        assertEquals(SortMode.NAME, SortMode.valueOf("NAME"))
        assertEquals(SortMode.UPDATED, SortMode.valueOf("UPDATED"))
        assertEquals(SortMode.LAUNCHES, SortMode.valueOf("LAUNCHES"))
    }
}
