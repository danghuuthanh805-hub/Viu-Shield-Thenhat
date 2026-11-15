
package com.viu.shield

object NativeGuard {
    init {
        System.loadLibrary("viu_guard")
    }

    external fun status(): String
}
