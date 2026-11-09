package com.example.turboboost

data class EngineResult(val applied: Int, val total: Int) {
    val score: String get() = "$applied/$total"
    val isOk: Boolean get() = applied > 0
}
