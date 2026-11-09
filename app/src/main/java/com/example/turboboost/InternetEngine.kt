package com.example.turboboost

object InternetEngine {

    private val SCRIPT = """
        ok=0; total=0
        _put() { total=$((total+1)); settings put "${'$'}1" "${'$'}2" "${'$'}3" >/dev/null 2>&1; [ "$(settings get "${'$'}1" "${'$'}2" 2>/dev/null)" = "${'$'}3" ] && ok=$((ok+1)); }
        _put global captive_portal_mode 0
        _put global captive_portal_https_url https://www.google.com/generate_204
        _put global captive_portal_http_url http://connectivitycheck.gstatic.com/generate_204
        _put global captive_portal_fallback_url http://www.google.com/gen_204
        _put global dns_resolver_sample_validity_seconds 1800
        _put global dns_resolver_success_threshold 4
        _put global dns_resolver_min_samples 8
        _put global dns_resolver_max_samples 64
        _put global ntp_server time.google.com
        _put global network_avoid_bad_wifi 1
        _put global wifi_score_low_rssi_threshold_24ghz -80
        _put global wifi_score_low_rssi_threshold_5ghz -73
        _put global wifi_scan_throttle_enabled 1
        _put global wifi_scan_always_enabled 0
        _put global wifi_verbose_logging_enabled 0
        _put global wifi_sleep_policy 2
        _put global wifi_watchdog_on 1
        _put global wifi_watchdog_poor_network_test_enabled 1
        _put global wifi_framework_scan_interval_ms 15000
        _put global wifi_networks_available_notification_on 0
        _put global mobile_data_always_on 1
        _put global gprs_data_transfer_preference 0
        _put global tether_dun_required 0
        _put global data_stall_alarm_aggressive_delay_in_ms 60000
        _put global data_stall_alarm_non_aggressive_delay_in_ms 300000
        _put global network_default_multipath_preference 2
        _put global tcp_default_init_rwnd 60
        _put global download_manager_max_bytes_over_mobile -1
        _put global download_manager_recommended_max_bytes_over_mobile -1
        _put global media_cache_max_size_mb 256
        _put global conn_timeout 5000
        _put global connectivity_metrics_buffer_size 0
        _put global network_watchlist_enabled 0
        _put global network_scoring_ui_enabled 1
        _put global network_recommendations_enabled 1
        _put global connectivity_check_duration_ms 5000
        _put global netstats_enabled 1
        _put global netstats_poll_interval 1800
        sysctl -w net.ipv4.tcp_fastopen=3 >/dev/null 2>&1 && ok=$((ok+1)); total=$((total+1))
        ndc resolver flushdefaultif >/dev/null 2>&1 && ok=$((ok+1)); total=$((total+1))
        am broadcast -a android.net.conn.CONNECTIVITY_CHANGE >/dev/null 2>&1 && ok=$((ok+1)); total=$((total+1))
        echo "${'$'}ok ${'$'}total"
    """.trimIndent()

    private val RESTORE_SCRIPT = """
        settings delete global captive_portal_mode
        settings delete global captive_portal_https_url
        settings delete global captive_portal_http_url
        settings delete global captive_portal_fallback_url
        settings delete global dns_resolver_sample_validity_seconds
        settings delete global dns_resolver_success_threshold
        settings delete global dns_resolver_min_samples
        settings delete global dns_resolver_max_samples
        settings delete global ntp_server
        settings delete global network_avoid_bad_wifi
        settings delete global wifi_score_low_rssi_threshold_24ghz
        settings delete global wifi_score_low_rssi_threshold_5ghz
        settings delete global wifi_scan_throttle_enabled
        settings delete global wifi_scan_always_enabled
        settings delete global wifi_verbose_logging_enabled
        settings delete global wifi_sleep_policy
        settings delete global wifi_watchdog_on
        settings delete global wifi_watchdog_poor_network_test_enabled
        settings delete global wifi_framework_scan_interval_ms
        settings delete global wifi_networks_available_notification_on
        settings delete global mobile_data_always_on
        settings delete global gprs_data_transfer_preference
        settings delete global tether_dun_required
        settings delete global data_stall_alarm_aggressive_delay_in_ms
        settings delete global data_stall_alarm_non_aggressive_delay_in_ms
        settings delete global network_default_multipath_preference
        settings delete global tcp_default_init_rwnd
        settings delete global download_manager_max_bytes_over_mobile
        settings delete global download_manager_recommended_max_bytes_over_mobile
        settings delete global media_cache_max_size_mb
        settings delete global conn_timeout
        settings delete global connectivity_metrics_buffer_size
        settings delete global network_watchlist_enabled
        settings delete global network_scoring_ui_enabled
        settings delete global network_recommendations_enabled
        settings delete global connectivity_check_duration_ms
        settings delete global netstats_enabled
        settings delete global netstats_poll_interval
        sysctl -w net.ipv4.tcp_fastopen=0 >/dev/null 2>&1
        echo done
    """.trimIndent()

    fun apply(): EngineResult {
        val out = SystemReader.read(SCRIPT).trim()
        val parts = out.split(Regex("\\s+"))
        val applied = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val total = parts.getOrNull(1)?.toIntOrNull() ?: 44
        return EngineResult(applied, total)
    }

    fun restore(): EngineResult {
        SystemReader.read(RESTORE_SCRIPT)
        return EngineResult(0, 0)
    }
}
