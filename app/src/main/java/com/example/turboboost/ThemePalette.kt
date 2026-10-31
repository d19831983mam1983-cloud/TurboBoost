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

    private val baseColors: List<Triple<String, Int, Int>> = listOf(
        Triple("Чёрно-оранжевый", 0xFFF2580A.toInt(), 0xFFFF8A3D.toInt()),
        Triple("Красная", 0xFFDC2626.toInt(), 0xFFEA580C.toInt()),
        Triple("Зелёная", 0xFF16A34A.toInt(), 0xFF10B981.toInt()),
        Triple("Синяя", 0xFF4F46E5.toInt(), 0xFF0EA5E9.toInt()),
        Triple("Фиолетовая", 0xFF9333EA.toInt(), 0xFFA855F7.toInt()),
        Triple("Бирюзовая", 0xFF0891B2.toInt(), 0xFF06B6D4.toInt()),
        Triple("Розовая", 0xFFDB2777.toInt(), 0xFFF472B6.toInt()),
        Triple("Жёлтая", 0xFFCA8A04.toInt(), 0xFFF59E0B.toInt()),
        Triple("Оранжевая", 0xFFEA580C.toInt(), 0xFFFB923C.toInt()),
        Triple("Мятная", 0xFF059669.toInt(), 0xFF34D399.toInt()),
        Triple("Ледяная", 0xFF0284C7.toInt(), 0xFF38BDF8.toInt()),
        Triple("Аврора", 0xFF7C3AED.toInt(), 0xFF06B6D4.toInt()),
        Triple("Неон", 0xFF00C896.toInt(), 0xFF00B8FF.toInt()),
        Triple("Серый терминал", 0xFFFF8A00.toInt(), 0xFFFFB347.toInt())
    )

    private fun blobColors(primary: Int, secondary: Int): Triple<Int, Int, Int> {
        val p = (primary and 0x00FFFFFF) or 0x66000000
        val s = (secondary and 0x00FFFFFF) or 0x44000000
        return Triple(p, s, p)
    }

    val themes: List<Theme> = buildList {
        baseColors.forEachIndexed { i, (name, p, s) ->
            val isGrid = i == baseColors.lastIndex
            val b = blobColors(p, s)
            add(Theme(name, p, s, b.first, b.second, b.third, isGrid))
        }
        baseColors.forEach { (name, p, s) ->
            val b = blobColors(p, s)
            add(Theme("$name · T", p, s, b.first, b.second, b.third, true))
        }
    }

    fun get(index: Int): Theme = themes[index.coerceIn(0, themes.size - 1)]

    val names: Array<String> = themes.map { it.name }.toTypedArray()
}
