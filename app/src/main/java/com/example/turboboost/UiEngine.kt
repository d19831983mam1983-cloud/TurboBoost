package com.example.turboboost

object UiEngine {

    private const val SCRIPT = """
        ok=0; total=0
        _put() { total=$((total+1)); settings put "${'$'}1" "${'$'}2" "${'$'}3" >/dev/null 2>&1; [ "$(settings get "${'$'}1" "${'$'}2" 2>/dev/null)" = "${'$'}3" ] && ok=$((ok+1)); }
        _sp()  { total=$((total+1)); setprop "${'$'}1" "${'$'}2" >/dev/null 2>&1; [ "$(getprop "${'$'}1" 2>/dev/null)" = "${'$'}2" ] && ok=$((ok+1)); }

        _put global window_animation_scale 0.5
        _put global transition_animation_scale 0.5
        _put global animator_duration_scale 0.5

        _put system show_touches 0
        _put system pointer_location 0
        _put global debug_view_attributes 0
        _put system show_refresh_rate 0
        _put global sysui_demo_on 0
        _put global sysui_tuner_demo_on 0
        _put global wait_for_debugger 0

        _put global debug_layout 0
        _put global show_layout_bounds 0
        _put global debug_gpu_overdraw 0
        _put system show_hw_layers_updates 0
        _put system show_screen_updates 0
        _put system show_surface_updates 0
        _put system show_non_rect_clip 0
        _put global verify_gpu_debug_layers 0
        _put system hwui_profile 0
        _put global enable_gpu_debug_layers 0

        _sp debug.hwui.show_dirty_regions 0
        _sp debug.hwui.show_layers_updates 0
        _sp debug.hwui.overdraw false
        _sp debug.hwui.profile false
        _sp debug.hwui.show_non_rect_clip 0
        _sp debug.hwui.show_draw 0

        _sp debug.sf.showupdates 0
        _sp debug.sf.showcpu 0
        _sp debug.sf.showbackground 0
        _sp debug.sf.showfps 0
        _sp debug.sf.showgpu 0
        _sp debug.sf.showrefresh 0
        _sp debug.sf.showpageflips 0
        _sp debug.sf.showlayers 0
        _sp debug.sf.ddms 0

        _sp debug.egl.trace 0
        _sp debug.egl.calltrace 0
        _sp debug.tracing.visualizer 0
        _sp debug.choreographer.profile false
        _sp debug.renderengine.debug 0

        echo "${'$'}ok ${'$'}total"
    """.trimIndent()

    private const val RESTORE_SCRIPT = """
        settings put global window_animation_scale 1.0
        settings put global transition_animation_scale 1.0
        settings put global animator_duration_scale 1.0
        settings delete system show_touches
        settings delete system pointer_location
        settings delete global debug_view_attributes
        settings delete system show_refresh_rate
        settings delete global sysui_demo_on
        settings delete global sysui_tuner_demo_on
        settings delete global wait_for_debugger
        settings delete global debug_layout
        settings delete global show_layout_bounds
        settings delete global debug_gpu_overdraw
        settings delete system show_hw_layers_updates
        settings delete system show_screen_updates
        settings delete system show_surface_updates
        settings delete system show_non_rect_clip
        settings delete global verify_gpu_debug_layers
        settings delete system hwui_profile
        settings delete global enable_gpu_debug_layers
        setprop debug.hwui.show_dirty_regions ""
        setprop debug.hwui.show_layers_updates ""
        setprop debug.hwui.overdraw ""
        setprop debug.hwui.profile ""
        setprop debug.hwui.show_non_rect_clip ""
        setprop debug.hwui.show_draw ""
        setprop debug.sf.showupdates ""
        setprop debug.sf.showcpu ""
        setprop debug.sf.showbackground ""
        setprop debug.sf.showfps ""
        setprop debug.sf.showgpu ""
        setprop debug.sf.showrefresh ""
        setprop debug.sf.showpageflips ""
        setprop debug.sf.showlayers ""
        setprop debug.sf.ddms ""
        setprop debug.egl.trace ""
        setprop debug.egl.calltrace ""
        setprop debug.tracing.visualizer ""
        setprop debug.choreographer.profile ""
        setprop debug.renderengine.debug ""
        echo done
    """.trimIndent()

    fun apply(): EngineResult {
        val out = SystemReader.read(SCRIPT, 15000L).trim()
        val parts = out.split(Regex("\\s+"))
        val applied = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val total = parts.getOrNull(1)?.toIntOrNull() ?: 40
        return EngineResult(applied, total)
    }

    fun restore(): EngineResult {
        SystemReader.read(RESTORE_SCRIPT, 15000L)
        return EngineResult(0, 0)
    }
}
