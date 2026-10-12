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

    init {
        viewModelScope.launch {
            val code = store.observeCode().firstOrNull()
            if (code != null) {
                val r = verifier.verify(code, today = todayProvider())
                if (r is VerifyResult.Valid) {
                    _state.value = LicenseState.Active(r.payload)
                } else {
                    // 之前激活过但现在签名/到期错 → 不阻塞用户重新登录
                    _state.value = LicenseState.Idle
                }
            }
        }
    }

    /** 测试用 setter,允许注入 verifier.重新跑一遍验签. */
    fun setVerifier(v: LicenseVerifier) {
        this.verifier = v
        viewModelScope.launch {
            val code = store.observeCode().firstOrNull()
            if (code != null) {
                val r = v.verify(code, today = todayProvider())
                if (r is VerifyResult.Valid) {
                    _state.value = LicenseState.Active(r.payload)
                }
            }
        }
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
