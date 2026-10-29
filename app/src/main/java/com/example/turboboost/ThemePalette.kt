package com.example.turboboost

object ThemePalette {

    data class Theme(
        val name: String,
        val primary: Int,
        val secondary: Int,
        val blob1: Int,
        val blob2: Int,
        val blob3: Int,
        val isGrid: Boolean = false
    )

    val themes: List<Theme> = listOf(
        Theme("Чёрно-оранжевый", 0xFFF2580A.toInt(), 0xFFFF8A3D.toInt(), 0x66F2580A, 0x66FF7A1A, 0x44FFB347),
        Theme("Красная", 0xFFDC2626.toInt(), 0xFFEA580C.toInt(), 0x66DC2626, 0x66EA580C, 0x44F59E0B),
        Theme("Зелёная", 0xFF16A34A.toInt(), 0xFF10B981.toInt(), 0x6616A34A, 0x6610B981, 0x4434D399),
        Theme("Синяя", 0xFF4F46E5.toInt(), 0xFF0EA5E9.toInt(), 0x664F46E5, 0x660EA5E9, 0x4438BDF8),
        Theme("Фиолетовая", 0xFF9333EA.toInt(), 0xFFA855F7.toInt(), 0x669333EA, 0x66A855F7, 0x44C084FC),
        Theme("Бирюзовая", 0xFF0891B2.toInt(), 0xFF06B6D4.toInt(), 0x660891B2, 0x6606B6D4, 0x4422D3EE),
        Theme("Розовая", 0xFFDB2777.toInt(), 0xFFF472B6.toInt(), 0x66DB2777, 0x66F472B6, 0x44FB923C),
        Theme("Жёлтая", 0xFFCA8A04.toInt(), 0xFFF59E0B.toInt(), 0x66CA8A04, 0x66F59E0B, 0x44FACC15),
        Theme("Оранжевая", 0xFFEA580C.toInt(), 0xFFFB923C.toInt(), 0x66EA580C, 0x66FB923C, 0x44FCA5A5),
        Theme("Мятная", 0xFF059669.toInt(), 0xFF34D399.toInt(), 0x66059669, 0x6634D399, 0x44A7F3D0),
        Theme("Ледяная", 0xFF0284C7.toInt(), 0xFF38BDF8.toInt(), 0x660284C7, 0x6638BDF8, 0x447DD3FC),
        Theme("Аврора", 0xFF7C3AED.toInt(), 0xFF06B6D4.toInt(), 0x667C3AED, 0x6606B6D4, 0x4438BDF8),
        Theme("Неон", 0xFF00C896.toInt(), 0xFF00B8FF.toInt(), 0x6600C896, 0x6600B8FF, 0x4466FFD1),
        Theme("Серый терминал", 0xFFFF8A00.toInt(), 0xFFFFB347.toInt(), 0x00FF8A00, 0x00FF8A00, 0x00FF8A00, true)
    )

    fun get(index: Int): Theme = themes[index.coerceIn(0, themes.size - 1)]

    val names: Array<String> = themes.map { it.name }.toTypedArray()
}
