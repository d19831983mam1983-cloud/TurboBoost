package com.example.turboboost

object ThemePalette {

    data class Theme(
        val name: String,
        val primary: Int,
        val secondary: Int,
        val blob1: Int,
        val blob2: Int,
        val blob3: Int
    )

    val themes: List<Theme> = listOf(
        Theme("Стоковая", 0xFF4F46E5.toInt(), 0xFF7BA7F5.toInt(), 0x664F46E5, 0x662563EB, 0x440D9488),
        Theme("Красная", 0xFFDC2626.toInt(), 0xFFEA580C.toInt(), 0x66DC2626, 0x66EA580C, 0x44F59E0B),
        Theme("Зелёная", 0xFF16A34A.toInt(), 0xFF10B981.toInt(), 0x6616A34A, 0x6610B981, 0x4434D399),
        Theme("Синяя", 0xFF4F46E5.toInt(), 0xFF0EA5E9.toInt(), 0x664F46E5, 0x660EA5E9, 0x4438BDF8),
        Theme("Фиолетовая", 0xFF9333EA.toInt(), 0xFFA855F7.toInt(), 0x669333EA, 0x66A855F7, 0x44C084FC),
        Theme("Бирюзовая", 0xFF0891B2.toInt(), 0xFF06B6D4.toInt(), 0x660891B2, 0x6606B6D4, 0x4422D3EE),
        Theme("Розовая", 0xFFDB2777.toInt(), 0xFFF472B6.toInt(), 0x66DB2777, 0x66F472B6, 0x44FB923C),
        Theme("Жёлтая", 0xFFCA8A04.toInt(), 0xFFF59E0B.toInt(), 0x66CA8A04, 0x66F59E0B, 0x44FACC15),
        Theme("Оранжевая", 0xFFEA580C.toInt(), 0xFFFB923C.toInt(), 0x66EA580C, 0x66FB923C, 0x44FCA5A5),
        Theme("Мятная", 0xFF059669.toInt(), 0xFF34D399.toInt(), 0x66059669, 0x6634D399, 0x44A7F3D0),
        Theme("Ледяная", 0xFF0284C7.toInt(), 0xFF38BDF8.toInt(), 0x660284C7, 0x6638BDF8, 0x447DD3FC)
    )

    fun get(index: Int): Theme = themes[index.coerceIn(0, themes.size - 1)]

    val names: Array<String> = themes.map { it.name }.toTypedArray()
}
