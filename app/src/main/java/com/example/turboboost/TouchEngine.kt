package com.example.turboboost

object TouchEngine {

    private val SCRIPT = """
        ok=0; total=0
        _put() { total=$((total+1)); settings put "${'$'}1" "${'$'}2" "${'$'}3" >/dev/null 2>&1; [ "$(settings get "${'$'}1" "${'$'}2" 2>/dev/null)" = "${'$'}3" ] && ok=$((ok+1)); }
        _dis() { total=$((total+1)); settings put "${'$'}1" "${'$'}2" 0 >/dev/null 2>&1; [ "$(settings get "${'$'}1" "${'$'}2" 2>/dev/null)" = "0" ] && ok=$((ok+1)); }
        _dis system show_touches
        _dis system pointer_location
        _dis system sound_effects_enabled
        _dis system lockscreen_sounds_enabled
        _dis global charging_sounds_enabled
        _put global window_animation_scale 0.5
        _put global transition_animation_scale 0.5
        _put global animator_duration_scale 0.5
        _dis global fancy_ime_animations
        _dis global disable_window_blurs
        _dis secure notification_bubbles
        _dis secure screensaver_enabled
        _dis system notification_light_pulse
        _dis secure notification_badging
        _put global always_finish_activities 0
        _dis system dtmf_tone
        _put global text_classifier_constants enabled=false
        _dis global smart_selection_animation_enabled
        _dis global enable_back_animation
        _dis secure accessibility_display_magnification_enabled
        _dis secure accessibility_autoclick_enabled
        _dis secure touch_exploration_enabled
        _put system pointer_speed 3
        _dis secure spell_checker_enabled
        _dis global wifi_scan_throttle_enabled
        _dis global network_scoring_ui_enabled
        _dis global verifier_verify_adb_installs
        setprop debug.hwui.show_dirty_regions 0 >/dev/null 2>&1 && ok=$((ok+1)); total=$((total+1))
        am kill-all >/dev/null 2>&1 && ok=$((ok+1)); total=$((total+1))
        pm trim-caches 1000M >/dev/null 2>&1 && ok=$((ok+1)); total=$((total+1))
        dumpsys gfxinfo --reset >/dev/null 2>&1 && ok=$((ok+1)); total=$((total+1))
        am broadcast -a android.intent.action.CONNECTIVITY_CHANGE >/dev/null 2>&1 && ok=$((ok+1)); total=$((total+1))
        ndc resolver flushdefaultif >/dev/null 2>&1 && ok=$((ok+1)); total=$((total+1))
        echo "${'$'}ok ${'$'}total"
    """.trimIndent()

    private val RESTORE_SCRIPT = """
        settings put system show_touches 0
        settings put system pointer_location 0
        settings put system sound_effects_enabled 1
        settings put system lockscreen_sounds_enabled 1
        settings put global charging_sounds_enabled 1
        settings put global window_animation_scale 1.0
        settings put global transition_animation_scale 1.0
        settings put global animator_duration_scale 1.0
        settings delete global fancy_ime_animations
        settings delete global disable_window_blurs
        settings delete secure notification_bubbles
        settings delete secure screensaver_enabled
        settings delete system notification_light_pulse
        settings delete secure notification_badging
        settings put global always_finish_activities 0
        settings delete system dtmf_tone
        settings delete global text_classifier_constants
        settings delete global smart_selection_animation_enabled
        settings delete global enable_back_animation
        settings delete secure accessibility_display_magnification_enabled
        settings delete secure accessibility_autoclick_enabled
        settings delete secure touch_exploration_enabled
        settings put system pointer_speed 0
        settings delete secure spell_checker_enabled
        settings delete global wifi_scan_throttle_enabled
        settings delete global network_scoring_ui_enabled
        settings delete global verifier_verify_adb_installs
        echo done
    """.trimIndent()

    fun apply(): EngineResult {
        val out = SystemReader.read(SCRIPT, 15000L).trim()
        val parts = out.split(Regex("\\s+"))
        val applied = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val total = parts.getOrNull(1)?.toIntOrNull() ?: 41
        return EngineResult(applied, total)
    }

    fun restore(): EngineResult {
        SystemReader.read(RESTORE_SCRIPT, 15000L)
        return EngineResult(0, 0)
    }
}
