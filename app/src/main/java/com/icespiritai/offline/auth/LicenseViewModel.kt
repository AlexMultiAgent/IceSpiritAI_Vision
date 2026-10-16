package com.icespiritai.offline.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed class LicenseState {
    data object Idle : LicenseState()
    data object Verifying : LicenseState()
    data class Active(val payload: LicensePayload) : LicenseState()
    data class Error(val result: VerifyResult) : LicenseState()
}

class LicenseViewModel internal constructor(
    private val store: LicenseStore,
    private val todayProvider: () -> Long
) : ViewModel() {

    @Volatile
    private var verifier: LicenseVerifier =
        LicenseVerifier.fromPublicKey(PublicKeyProvider.default)

    private val _state = MutableStateFlow<LicenseState>(LicenseState.Idle)
    val state: StateFlow<LicenseState> = _state.asStateFlow()

    /**
     * 当前这次「读已存激活码 + 验签」的 Job 句柄。
     *
     * 之所以把句柄暴露出来(`internal`,只给同 module 的测试用):这次读取在
     * `viewModelScope`(Main.immediate)上启动,却是在 DataStore 的 IO 线程上
     * 完成,最后要回到 Main 写 [state]。测试如果在它回来之前就
     * `Dispatchers.resetMain()`,那条协程会撞上 `TestMainDispatcher` 的并发检查,
     * 报 `IllegalStateException: Dispatchers.Main is used concurrently with setting it`
     * —— 2026-10-15 审计里全量单测偶发变红的就是这一条。测试 [Job.join] 这个句柄
     * 之后再拆 Main 调度器,teardown 才是确定性的。生产代码不使用这个句柄。
     */
    internal var initialLoadJob: Job = viewModelScope.launch { loadStoredCode(verifier) }
        private set

    /**
     * 读一次已保存的激活码并按 [v] 验签。
     *
     * 没有存过码 → 什么都不做(保持 [LicenseState.Idle],等用户输入);
     * 存过但现在验签不过(换钥 / 过期 / 损坏)→ 也保持 Idle,不阻塞重新登录。
     */
    private suspend fun loadStoredCode(v: LicenseVerifier) {
        val code = store.observeCode().firstOrNull() ?: return
        val r = v.verify(code, today = todayProvider())
        if (r is VerifyResult.Valid) {
            _state.value = LicenseState.Active(r.payload)
        } else {
            _state.value = LicenseState.Idle
        }
    }

    /** 测试用 setter,允许注入 verifier.重新跑一遍验签. */
    fun setVerifier(v: LicenseVerifier) {
        this.verifier = v
        // 先收掉上一次读取:两次并发读取会各自写 _state,后落定的那次可能覆盖
        // 掉结果;调用方(测试)也只需要 join [initialLoadJob] 这一个句柄。
        initialLoadJob.cancel()
        initialLoadJob = viewModelScope.launch { loadStoredCode(v) }
    }

    /**
     * Submit a license code for verification. Returns the [Job] so callers
     * (mainly tests) can [Job.join] until verify + DataStore.setCode finish.
     * Production callers can ignore the return — UI observes [state].
     */
    fun submit(username: String, code: String): Job {
        _state.value = LicenseState.Verifying
        return viewModelScope.launch {
            val r = verifier.verify(code, today = todayProvider(), claimedUsername = username)
            when (r) {
                is VerifyResult.Valid -> {
                    store.setCode(code)
                    _state.value = LicenseState.Active(r.payload)
                }
                else -> {
                    _state.value = LicenseState.Error(r)
                }
            }
        }
    }

    /** 测试用:强制设置状态.产品代码不要调。 */
    @Deprecated("test only")
    fun forceStateForTest(s: LicenseState) {
        _state.value = s
    }

    /** Internal-exposed constructor for tests that want to pre-seed verifier. */
    internal constructor(
        store: LicenseStore,
        verifier: LicenseVerifier,
        todayProvider: () -> Long
    ) : this(store, todayProvider) {
        this.verifier = verifier
    }

    companion object {
        fun factory(store: LicenseStore, todayProvider: () -> Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LicenseViewModel(store, todayProvider) as T
                }
            }
    }
}
