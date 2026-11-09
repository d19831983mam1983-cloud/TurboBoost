package com.example.turboboost

object LaunchEngine {

    private const val SCRIPT = """
        ok=0; total=0
        PKG="${'$'}1"
        _put() { total=$((total+1)); settings put "${'$'}1" "${'$'}2" "${'$'}3" >/dev/null 2>&1; [ "$(settings get "${'$'}1" "${'$'}2" 2>/dev/null)" = "${'$'}3" ] && ok=$((ok+1)); }
        _dc()  { total=$((total+1)); cmd device_config put "${'$'}1" "${'$'}2" "${'$'}3" >/dev/null 2>&1; [ "$(cmd device_config get "${'$'}1" "${'$'}2" 2>/dev/null | tr -d '\r')" = "${'$'}3" ] && ok=$((ok+1)); }
        _bucket() { total=$((total+1)); am set-standby-bucket "${'$'}1" active >/dev/null 2>&1; b="$(am get-standby-bucket "${'$'}1" 2>/dev/null | tr -d '[:space:]')"; case "${'$'}b" in ACTIVE|active|10) ok=$((ok+1));; esac; }
        _dc activity_manager service_bg_timeout_ms 15000
        _dc activity_manager service_restart_duration 10000
        _dc activity_manager background_settle_time 60000
        _dc activity_manager max_cached_processes 32
        _dc activity_manager max_phantom_processes 2147483647
        _dc activity_manager usap_pool_enabled true
        _dc activity_manager binder_transaction_buffer_size 1048576
        _dc activity_manager service_start_foreground_timeout_ms 30000
        _put global always_finish_activities 0
        if [ -n "${'$'}PKG" ]; then
          total=$((total+1)); cmd package compile -m speed-profile -f "${'$'}PKG" >/dev/null 2>&1 && ok=$((ok+1))
          _bucket "${'$'}PKG"
          total=$((total+1)); cmd appops set "${'$'}PKG" RUN_IN_BACKGROUND allow >/dev/null 2>&1 && ok=$((ok+1))
          total=$((total+1)); cmd appops set "${'$'}PKG" RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1 && ok=$((ok+1))
          total=$((total+1)); dumpsys deviceidle whitelist +"${'$'}PKG" >/dev/null 2>&1 && ok=$((ok+1))
        fi
        echo "${'$'}ok ${'$'}total"
    """.trimIndent()

    private const val RESTORE_SCRIPT = """
        cmd device_config delete activity_manager service_bg_timeout_ms
        cmd device_config delete activity_manager service_restart_duration
        cmd device_config delete activity_manager background_settle_time
        cmd device_config delete activity_manager max_cached_processes
        cmd device_config delete activity_manager max_phantom_processes
        cmd device_config delete activity_manager usap_pool_enabled
        cmd device_config delete activity_manager binder_transaction_buffer_size
        cmd device_config delete activity_manager service_start_foreground_timeout_ms
        settings put global always_finish_activities 0
        echo done
    """.trimIndent()

    fun apply(pkg: String): EngineResult {
        val safePkg = if (pkg.isBlank()) "" else pkg.replace("\"", "")
        val out = SystemReader.read(SCRIPT, 15000L, safePkg).trim()
        val parts = out.split(Regex("\\s+"))
        val applied = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val total = parts.getOrNull(1)?.toIntOrNull() ?: 9
        return EngineResult(applied, total)
    }

    fun restore(): EngineResult {
        SystemReader.read(RESTORE_SCRIPT, 15000L)
        return EngineResult(0, 0)
    }
}
