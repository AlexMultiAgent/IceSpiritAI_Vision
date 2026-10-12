package com.icespiritai.offline.auth

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LicenseStoreTest {

    private fun store() = LicenseStore(ApplicationProvider.getApplicationContext())

    @Test
    fun empty_initially_returns_null() = runTest {
        assertNull(store().observeCode().firstOrNull())
    }

    @Test
    fun roundtrip_code() = runTest {
        val s = store()
        val code = "ICS1.eyJ2IjoxLC0gSWQiOiJhMWIyYzMiLCJuIjogImFjbWUiLCJlIjoyMDI3MDYzMDl9.AAAAAA"
        s.setCode(code)
        assertEquals(code, s.observeCode().firstOrNull())
    }

    @Test
    fun setCode_overwrites_previous() = runTest {
        val s = store()
        s.setCode("ICS1.first.code")
        s.setCode("ICS1.second.code")
        assertEquals("ICS1.second.code", s.observeCode().firstOrNull())
    }

    @Test
    fun clear_removes_code() = runTest {
        val s = store()
        s.setCode("ICS1.x.y")
        s.clear()
        assertNull(s.observeCode().firstOrNull())
    }
}
