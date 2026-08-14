package android.net

import android.os.Parcel

// Minimal Uri subclass for unit tests running under
// unitTests.isReturnDefaultValues=true. Lives in package android.net
// because android.net.Uri's no-arg constructor is package-private.
internal class StubUri : Uri() {
    override fun getScheme(): String? = null
    override fun getSchemeSpecificPart(): String? = null
    override fun getEncodedSchemeSpecificPart(): String? = null
    override fun getAuthority(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String? = null
    override fun getPort(): Int = -1
    override fun getPath(): String? = null
    override fun getEncodedPath(): String? = null
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): MutableList<String> = mutableListOf()
    override fun getLastPathSegment(): String? = null
    override fun isAbsolute(): Boolean = false
    override fun isHierarchical(): Boolean = false
    override fun isRelative(): Boolean = true
    override fun toString(): String = "stub://uri"
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
    override fun describeContents(): Int = 0
    override fun writeToParcel(out: Parcel, flags: Int) {
        out.writeString(toString())
    }
    override fun buildUpon(): Uri.Builder = throw UnsupportedOperationException()
}