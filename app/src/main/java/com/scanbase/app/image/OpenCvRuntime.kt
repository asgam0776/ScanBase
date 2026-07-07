package com.scanbase.app.image

import org.opencv.android.OpenCVLoader

object OpenCvRuntime {
    private var initialized = false

    fun initialize(): Boolean {
        if (!initialized) {
            initialized = OpenCVLoader.initLocal()
        }
        return initialized
    }

    fun isInitialized(): Boolean {
        return initialized
    }
}
