package com.example.strivo.core.util

import java.util.Locale

/** 40.0 -> "40", 42.5 -> "42.5". */
fun formatWeight(kg: Double): String = if (kg % 1.0 == 0.0) kg.toInt().toString() else kg.toString()

/** 12345.6 -> "12,346 kg": a total, to the whole kilo. */
fun formatKg(kg: Double): String = "%,.0f kg".format(Locale.getDefault(), kg)
