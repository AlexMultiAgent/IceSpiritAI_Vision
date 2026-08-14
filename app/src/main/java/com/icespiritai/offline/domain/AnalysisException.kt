package com.icespiritai.offline.domain

class OcrEngineUnavailable(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class OcrFailed(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class RuleLoadFailed(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
