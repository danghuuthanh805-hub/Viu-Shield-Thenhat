
package com.viu.shield

object NativeGuard {

    init {
        System.loadLibrary("viu")
    }

    external fun status(): String

    external fun version(): String

    external fun selfTest(): Boolean

    external fun execute(
        operation: Int,
        a: Double,
        b: Double
    ): Double
}
