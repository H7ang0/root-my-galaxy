package com.h7ang0.root

object NativeProbe {
    init {
        System.loadLibrary("s25u_native")
    }

    external fun run(): String

    external fun isKernelSuActive(): Boolean
}
