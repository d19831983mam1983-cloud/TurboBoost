package com.example.turboboost

object GpuEngine {

    private const val SCRIPT = """
        ok=0; total=0
        _put() { total=$((total+1)); settings put "${'$'}1" "${'$'}2" "${'$'}3" >/dev/null 2>&1; [ "$(settings get "${'$'}1" "${'$'}2" 2>/dev/null)" = "${'$'}3" ] && ok=$((ok+1)); }
        _sp()  { total=$((total+1)); setprop "${'$'}1" "${'$'}2" >/dev/null 2>&1; [ "$(getprop "${'$'}1" 2>/dev/null)" = "${'$'}2" ] && ok=$((ok+1)); }
        _put global force_hw_accel 1
        _sp debug.hwui.texture_cache_size 72
        _sp debug.hwui.layer_cache_size 48
        _sp debug.hwui.path_cache_size 32
        _sp debug.hwui.shape_cache_size 4
        _sp debug.hwui.gradient_cache_size 2
        _sp debug.hwui.drop_shadow_cache_size 6
        _sp debug.hwui.text_small_cache_width 1024
        _sp debug.hwui.text_small_cache_height 1024
        _sp debug.hwui.text_large_cache_width 4096
        _sp debug.hwui.text_large_cache_height 2048
        _sp debug.hwui.patch_cache_size 512
        _sp debug.hwui.render_ahead 1
        _sp debug.hwui.renderer skiagl
        _sp debug.renderengine.multithread 1
        _sp debug.hwc.force_gpu_vsync 1
        _sp debug.sf.disable_hwc_overlays 0
        _sp debug.hwui.render_dirty_regions true
        _sp debug.egl.preserve_context 1
        _sp debug.hwc.use_vr_flinger 0
        _sp debug.egl.shader_cache 1
        _sp debug.egl.shader_cache_size 4194304
        _sp debug.egl.profiler 0
        _sp debug.egl.force_msaa false
        _sp debug.hwui.texture_cache_flush_rate 0.6
        _sp debug.vulkan.deopt.disable 1
        _sp debug.egl.swapinterval 0
        _sp debug.sf.disable_backpressure 1
        _sp debug.sf.gpu_comp_tiling 1
        _sp debug.sf.latch_unsignaled 1
        _sp debug.sf.set_idle_timer_ms 0
        _sp debug.sf.enable_gl_backpressure 1
        _sp debug.sf.enable_hwc_vds 0
        _sp debug.sf.use_content_detection_v2 0
        _sp debug.sf.enable_transaction_tracing 0
        _put global disable_window_blurs 0
        _put secure notification_bubbles 0
        _put global wifi_scan_throttle_enabled 1
        _sp debug.enable.gpu.debuglayers 0
        _put global enable_gpu_debug_layers 0
        _sp debug.egl.traceGpuCompletion 0
        _put global enable_opengl_traces 0
        _sp debug.hwui.show_layers_updates 0
        _sp debug.hwui.profile false
        _sp debug.atrace.tags.enableflags 0
        _sp debug.sf.showfps 0
        _sp debug.sf.showupdates 0
        _sp debug.sf.dump 0
        _put global debug_gpu_overdraw false
        _sp debug.hwui.show_overdraw false
        _sp debug.hwui.force_dark false
        echo "${'$'}ok ${'$'}total"
    """.trimIndent()

    private const val RESTORE_SCRIPT = """
        settings delete global force_hw_accel
        setprop debug.hwui.texture_cache_size ""
        setprop debug.hwui.layer_cache_size ""
        setprop debug.hwui.path_cache_size ""
        setprop debug.hwui.shape_cache_size ""
        setprop debug.hwui.gradient_cache_size ""
        setprop debug.hwui.drop_shadow_cache_size ""
        setprop debug.hwui.render_ahead ""
        setprop debug.hwui.renderer skiagl
        setprop debug.renderengine.multithread ""
        setprop debug.hwc.force_gpu_vsync ""
        setprop debug.hwui.render_dirty_regions ""
        setprop debug.egl.preserve_context ""
        setprop debug.egl.shader_cache ""
        setprop debug.egl.shader_cache_size ""
        setprop debug.egl.swapinterval ""
        setprop debug.sf.disable_backpressure ""
        setprop debug.sf.latch_unsignaled ""
        setprop debug.sf.set_idle_timer_ms ""
        setprop debug.sf.enable_gl_backpressure ""
        settings put secure notification_bubbles 1
        settings put global wifi_scan_throttle_enabled 0
        setprop debug.hwui.show_layers_updates 0
        setprop debug.hwui.profile false
        setprop debug.sf.showfps 0
        setprop debug.sf.showupdates 0
        setprop debug.hwui.show_overdraw false
        setprop debug.hwui.force_dark false
        echo done
    """.trimIndent()

    fun apply(): EngineResult {
        val out = SystemReader.read(SCRIPT).trim()
        val parts = out.split(Regex("\\s+"))
        val applied = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val total = parts.getOrNull(1)?.toIntOrNull() ?: 55
        return EngineResult(applied, total)
    }

    fun restore(): EngineResult {
        SystemReader.read(RESTORE_SCRIPT)
        return EngineResult(0, 0)
    }
}
