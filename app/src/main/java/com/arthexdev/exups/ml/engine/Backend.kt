package com.arthexdev.exups.ml.engine

enum class Backend(val label: String) {
    AUTO("Auto"),
    GPU("GPU"),
    NNAPI("NNAPI"),
    CPU("CPU")
}
