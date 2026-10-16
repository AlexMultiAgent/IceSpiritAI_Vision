package com.icespiritai.offline.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * LicenseViewModelTest — exercises the state machine end-to-end.
 *
 * Dispatcher plumbing: each test installs [UnconfinedTestDispatcher] as the
 * Main dispatcher and wraps the body in [runBlocking] so [Dispatchers.IO]
 * (used internally by DataStore.edit) actually completes on real JVM threads
 * while we await it. [LicenseViewModel.submit] returns the launched [Job] so
 * the test can `.join()` until verify + persist settle, then assert state.
 *
 * **Every test must [settled] (or otherwise join) the ViewModel's
 * [LicenseViewModel.initialLoadJob] before `Dispatchers.resetMain()`.** The
 * constructor-time read runs on `viewModelScope` (Main.immediate) but finishes
 * on a DataStore IO thread, so resetting Main while it is still coming back
 * trips `TestMainDispatcher`'s concurrency check:
 * `IllegalStateException: Dispatchers.Main is used concurrently with setting it`
 * — the intermittent CI failure observed on 2026-10-15.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LicenseViewModelTest {

    private val keyPair = java.security.KeyPairGenerator.getInstance("EC").run {
        initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    private val altKeyPair = java.security.KeyPairGenerator.getInstance("EC").run {
        initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    private fun sign(pk: java.security.PrivateKey, data: ByteArray): ByteArray {
        val s = java.security.Signature.getInstance("SHA256withECDSA")
        s.initSign(pk)
        s.update(data)
        return s.sign()
    }

    private fun makeCode(
        kp: java.security.KeyPair = keyPair,
        e: Long = 20270630,
        n: String = "acme"
    ): String {
        val p = LicensePayload(1, n, e)
        val bytes = LicenseCodec.encodePayload(p)
        return LicenseCodec.format(bytes, sign(kp.private, bytes))
    }

    private fun freshVm(today: Long = 20260101L): LicenseViewModel {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        return LicenseViewModel(LicenseStore(ctx), todayProvider = { today })
    }

    @Test
    fun initial_state_is_idle() {
        val main = UnconfinedTestDispatcher()
        Dispatchers.setMain(main)
        try {
            val vm = freshVm().settled()
            assertEquals(LicenseState.Idle, vm.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 等这次 VM 的初始读取(或 [LicenseViewModel.setVerifier] 触发的重读)落定。
     *
     * 见类 KDoc:不 join 就 `Dispatchers.resetMain()` 会与"刚读完 DataStore、
     * 正要回到 Main 写 state"的那条协程撞车,偶发
     * `IllegalStateException: Dispatchers.Main is used concurrently with setting it`。
     * join 之后该协程已彻底结束(不是"多半结束了"),teardown 因此是确定性的。
     */
    private fun LicenseViewModel.settled(): LicenseViewModel = also {
        runBlocking { it.initialLoadJob.join() }
    }

    @Test
    fun submit_with_valid_code_becomes_active() = runBlocking {
        val main = UnconfinedTestDispatcher()
        Dispatchers.setMain(main)
        try {
            val code = makeCode()
            val vm = freshVm()
                .also { it.setVerifier(LicenseVerifier.fromPublicKey(keyPair.public)) }
                .settled()
            vm.submit(username = "acme", code = code).join()
            val s = vm.state.value
            assertTrue("expected Active but was $s", s is LicenseState.Active)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun submit_with_signature_mismatch_returns_error() = runBlocking {
        val main = UnconfinedTestDispatcher()
        Dispatchers.setMain(main)
        try {
            val code = makeCode(kp = keyPair)
            val vm = freshVm()
                .also { it.setVerifier(LicenseVerifier.fromPublicKey(altKeyPair.public)) }
                .settled()
            vm.submit(username = "acme", code = code).join()
            assertEquals(LicenseState.Error(VerifyResult.SignatureMismatch), vm.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun submit_with_username_mismatch_returns_error() = runBlocking {
        val main = UnconfinedTestDispatcher()
        Dispatchers.setMain(main)
        try {
            val code = makeCode(n = "acme")
            val vm = freshVm()
                .also { it.setVerifier(LicenseVerifier.fromPublicKey(keyPair.public)) }
                .settled()
            vm.submit(username = "wrong", code = code).join()
            assertEquals(LicenseState.Error(VerifyResult.UsernameMismatch("acme")), vm.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun submit_with_expired_returns_error() = runBlocking {
        val main = UnconfinedTestDispatcher()
        Dispatchers.setMain(main)
        try {
            val code = makeCode(e = 20260101)
            // today must be > e for expiry
            val vm = freshVm(today = 20260102)
                .also { it.setVerifier(LicenseVerifier.fromPublicKey(keyPair.public)) }
                .settled()
            vm.submit(username = "acme", code = code).join()
            assertEquals(LicenseState.Error(VerifyResult.Expired), vm.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun submit_with_garbage_returns_parse_failed() = runBlocking {
        val main = UnconfinedTestDispatcher()
        Dispatchers.setMain(main)
        try {
            val vm = freshVm()
                .also { it.setVerifier(LicenseVerifier.fromPublicKey(keyPair.public)) }
                .settled()
            vm.submit(username = "acme", code = "ICS1.!!!.c2ln").join()
            assertEquals(LicenseState.Error(VerifyResult.ParseFailed), vm.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun active_persists_and_re_loads_as_active() = runBlocking {
        val main = UnconfinedTestDispatcher()
        Dispatchers.setMain(main)
        try {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            val code = makeCode()
            // 先激活
            val vm1 = freshVm()
                .also { it.setVerifier(LicenseVerifier.fromPublicKey(keyPair.public)) }
                .settled()
            vm1.submit("acme", code).join()
            assertTrue(vm1.state.value is LicenseState.Active)
            // 直接从 store 读取确认持久化（确定性,不等 vm2 的 init 协程）
            val store = LicenseStore(ctx)
            assertEquals(code, store.observeCode().firstOrNull())
            // 新 VM 加载(同一 store) — init 协程读 vm1 刚写下的码并验签。
            val vm2 = LicenseViewModel(store, { 20260101L })
                .also { it.setVerifier(LicenseVerifier.fromPublicKey(keyPair.public)) }
                .settled()
            assertTrue("expected Active but was ${vm2.state.value}", vm2.state.value is LicenseState.Active)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun stored_valid_code_becomes_active_once_the_initial_load_settles() = runBlocking {
        val main = UnconfinedTestDispatcher()
        Dispatchers.setMain(main)
        try {
            val ctx = ApplicationProvider.getApplicationContext<Context>()
            val store = LicenseStore(ctx)
            store.setCode(makeCode())

            val vm = LicenseViewModel(store, { 20260101L })
                .also { it.setVerifier(LicenseVerifier.fromPublicKey(keyPair.public)) }
                .settled()

            val s = vm.state.value
            assertTrue("expected Active but was $s", s is LicenseState.Active)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
