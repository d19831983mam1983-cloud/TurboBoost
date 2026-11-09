package com.example.turboboost

object ShellNotify {
    fun send(title: String, body: String) {
        val safeTitle = title.replace("\"", "")
        val safeBody = body.replace("\"", "")
        val tag = "znak_" + title.lowercase().replace(" ", "_")
        
        val cmd = "cmd notification post -S bigtext -t \"$safeTitle\" \"$tag\" \"$safeBody\""
        
        SystemReader.read(cmd)
    }
}
