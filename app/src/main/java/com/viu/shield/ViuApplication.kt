
package com.viu.shield

import android.app.Application
import android.util.Log

class ViuApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            val status = NativeGuard.status()
            val version = NativeGuard.version()
            val working = NativeGuard.selfTest()

            Log.i("ViUStub", status)
            Log.i("ViUStub", version)
            Log.i("ViUStub", "Self test: $working")

        } catch (e: UnsatisfiedLinkError) {
            Log.e("ViUStub", "Native library unavailable", e)
        }
    }
}
