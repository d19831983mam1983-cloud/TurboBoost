package com.example.turboboost

object RamEngine {

    private val SCRIPT = """
        ok=0; total=0
        _dc() { total=$((total+1)); cmd device_config put "${'$'}1" "${'$'}2" "${'$'}3" >/dev/null 2>&1; [ "$(cmd device_config get "${'$'}1" "${'$'}2" 2>/dev/null | tr -d '\r')" = "${'$'}3" ] && ok=$((ok+1)); }
        _put() { total=$((total+1)); settings put "${'$'}1" "${'$'}2" "${'$'}3" >/dev/null 2>&1; [ "$(settings get "${'$'}1" "${'$'}2" 2>/dev/null)" = "${'$'}3" ] && ok=$((ok+1)); }
        _sp()  { total=$((total+1)); setprop "${'$'}1" "${'$'}2" >/dev/null 2>&1; [ "$(getprop "${'$'}1" 2>/dev/null)" = "${'$'}2" ] && ok=$((ok+1)); }
        _dc activity_manager use_compaction true
        _dc activity_manager_native_boot use_freezer true
        _dc activity_manager max_cached_processes 64
        _dc activity_manager max_phantom_processes 2147483647
        _dc activity_manager bg_auto_restrict_abusive_apps 1
        _dc activity_manager bg_current_drain_auto_restrict_abusive_apps_enabled 1
        _dc device_idle light_after_inactive_to 300000
        _dc device_idle light_pre_idle_to 60000
        _dc device_idle light_idle_to 60000
        _dc device_idle light_idle_timeout 300000
        _dc device_idle deepen_to 120000
        _dc device_idle light_idle_maintenance_min_budget 10000
        _dc device_idle light_idle_maintenance_max_budget 30000
        _dc device_idle min_light_maintenance_time 5000
        _dc device_idle min_deep_maintenance_time 8000
        _put global app_standby_enabled 1
        _put secure adaptive_battery_management_enabled 1
        _put global always_finish_activities 0
        _put global cached_apps_freezer 1
        _sp ro.lmk.use_psi true
        _sp ro.lmk.psi_partial_stall_ms 70
        _sp ro.lmk.psi_complete_stall_ms 150
        _sp ro.lmk.thrashing_limit 30
        _sp ro.lmk.thrashing_limit_decay 10
        _sp ro.lmk.kill_heaviest_task true
        _sp ro.lmk.swap_free_low_percentage 10
        _sp ro.lmk.use_minfree_levels true
        _sp ro.lmk.debug false
        _sp ro.lmk.kill_timeout_ms 200
        _sp dalvik.vm.heaptargetutilization 0.75
        _sp dalvik.vm.heapminfree 2m
        _sp dalvik.vm.gctype CMC
        _sp dalvik.vm.usejit true
        _sp dalvik.vm.usejitprofiles true
        _sp persist.logd.size 512K
        am idle-maintenance >/dev/null 2>&1 && ok=$((ok+1)); total=$((total+1))
        am kill-all >/dev/null 2>&1 && ok=$((ok+1)); total=$((total+1))
        pm trim-caches 1000M >/dev/null 2>&1 && ok=$((ok+1)); total=$((total+1))
        am compact system >/dev/null 2>&1 && ok=$((ok+1)); total=$((total+1))
        echo "${'$'}ok ${'$'}total"
    """.trimIndent()

    private val RESTORE_SCRIPT = """
        cmd device_config delete activity_manager use_compaction
        cmd device_config delete activity_manager_native_boot use_freezer
        cmd device_config delete activity_manager max_cached_processes
        cmd device_config delete activity_manager max_phantom_processes
        cmd device_config delete activity_manager bg_auto_restrict_abusive_apps
        cmd device_config delete activity_manager bg_current_drain_auto_restrict_abusive_apps_enabled
        cmd device_config delete device_idle light_after_inactive_to
        cmd device_config delete device_idle light_pre_idle_to
        cmd device_config delete device_idle light_idle_to
        cmd device_config delete device_idle light_idle_timeout
        cmd device_config delete device_idle deepen_to
        cmd device_config delete device_idle light_idle_maintenance_min_budget
        cmd device_config delete device_idle light_idle_maintenance_max_budget
        cmd device_config delete device_idle min_light_maintenance_time
        cmd device_config delete device_idle min_deep_maintenance_time
        settings delete global app_standby_enabled
        settings delete secure adaptive_battery_management_enabled
        settings delete global cached_apps_freezer
        echo done
    """.trimIndent()

    fun apply(): EngineResult {
        val out = SystemReader.read(SCRIPT, 15000L).trim()
        val parts = out.split(Regex("\\s+"))
        val applied = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val total = parts.getOrNull(1)?.toIntOrNull() ?: 43
        return EngineResult(applied, total)
    }

    fun restore(): EngineResult {
        SystemReader.read(RESTORE_SCRIPT, 15000L)
        return EngineResult(0, 0)
    }
}
