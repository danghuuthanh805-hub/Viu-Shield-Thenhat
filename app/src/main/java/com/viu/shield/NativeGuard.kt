package com.viu.shield

object NativeGuard {
    private var loaded = false

    init {
        loaded = try {
            System.loadLibrary("viu_guard")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        } catch (e: SecurityException) {
            false
        }
    }

    fun status(): String {
        return if (loaded) {
            try {
                nativeStatus()
            } catch (e: UnsatisfiedLinkError) {
                "NATIVE_LIBRARY_ERROR"
            }
        } else {
            "NATIVE_LIBRARY_MISSING"
        }
    }

    private external fun nativeStatus(): String
}
