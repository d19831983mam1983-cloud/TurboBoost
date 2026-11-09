package com.example.turboboost

object FpsEngine {

    private const val SCRIPT = """
        ok=0; total=0
        _put() { total=$((total+1)); settings put "${'$'}1" "${'$'}2" "${'$'}3" >/dev/null 2>&1; [ "$(settings get "${'$'}1" "${'$'}2" 2>/dev/null)" = "${'$'}3" ] && ok=$((ok+1)); }
        _put global refresh_rate_mode 1
        _put global window_animation_scale 0.25
        _put global transition_animation_scale 0.25
        _put global animator_duration_scale 0.25
        _put global fancy_ime_animations 0
        _put system pointer_speed 0
        _put secure long_press_timeout 300
        _put secure multi_press_timeout 300
        _put system show_touches 0
        _put system pointer_location 0
        _put global background_process_limit 8
        _put global settings_enable_monitor_phantom_procs 1
        _put global disable_window_blurs 1
        _put global enable_back_animation 0
        _put global smart_selection_animation_enabled 0
        _put global text_classifier_constants enabled=false
        echo "${'$'}ok ${'$'}total"
    """.trimIndent()

    private const val RESTORE_SCRIPT = """
        settings put global window_animation_scale 1.0
        settings put global transition_animation_scale 1.0
        settings put global animator_duration_scale 1.0
        settings delete global refresh_rate_mode
        settings delete global background_process_limit
        settings delete global settings_enable_monitor_phantom_procs
        settings delete global disable_window_blurs
        settings delete global text_classifier_constants
        settings delete global enable_back_animation
        settings delete global smart_selection_animation_enabled
        echo done
    """.trimIndent()

    fun apply(): EngineResult {
        val out = SystemReader.read(SCRIPT).trim()
        val parts = out.split(Regex("\\s+"))
        val applied = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val total = parts.getOrNull(1)?.toIntOrNull() ?: 16
        return EngineResult(applied, total)
    }

    fun restore(): EngineResult {
        SystemReader.read(RESTORE_SCRIPT)
        return EngineResult(0, 0)
    }
}
