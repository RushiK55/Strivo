package com.example.strivo.domain.analytics

/**
 * Everyday foods with typical values for one serving, so a meal can be logged without knowing its calories. They are
 * averages: a home-cooked dal and a restaurant one differ, so the numbers can be changed before saving.
 */
data class CommonFood(
    val name: String,
    /** What one serving is, e.g. "1 medium (40 g)". */
    val serving: String,
    val calories: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

val CommonFoods: List<CommonFood> = listOf(
    CommonFood("Roti / chapati", "1 medium (40 g)", 120, 3.5, 22.0, 2.5),
    CommonFood("Paratha", "1 plain (80 g)", 260, 5.0, 36.0, 10.0),
    CommonFood("White rice", "1 cup cooked (160 g)", 205, 4.3, 45.0, 0.4),
    CommonFood("Brown rice", "1 cup cooked (160 g)", 215, 5.0, 45.0, 1.8),
    CommonFood("Dal", "1 bowl (200 g)", 220, 12.0, 30.0, 5.0),
    CommonFood("Rajma", "1 bowl (200 g)", 230, 13.0, 36.0, 3.0),
    CommonFood("Chole", "1 bowl (200 g)", 280, 12.0, 36.0, 9.0),
    CommonFood("Mixed vegetable sabzi", "1 bowl (150 g)", 130, 3.0, 14.0, 7.0),
    CommonFood("Salad", "1 bowl", 60, 2.0, 10.0, 1.0),
    CommonFood("Paneer", "100 g", 265, 18.0, 3.5, 20.0),
    CommonFood("Egg, boiled", "1 large", 78, 6.3, 0.6, 5.3),
    CommonFood("Omelette", "2 eggs with oil", 190, 13.0, 1.5, 15.0),
    CommonFood("Chicken breast", "100 g cooked", 165, 31.0, 0.0, 3.6),
    CommonFood("Chicken curry", "1 bowl (200 g)", 300, 25.0, 8.0, 19.0),
    CommonFood("Fish, grilled", "100 g", 140, 24.0, 0.0, 4.5),
    CommonFood("Biryani, chicken", "1 plate (300 g)", 500, 24.0, 62.0, 16.0),
    CommonFood("Milk", "1 glass (250 ml)", 150, 8.0, 12.0, 8.0),
    CommonFood("Curd / dahi", "1 bowl (150 g)", 90, 5.0, 7.0, 5.0),
    CommonFood("Whey protein", "1 scoop (30 g)", 120, 24.0, 3.0, 1.5),
    CommonFood("Oats", "40 g dry, cooked", 150, 5.0, 27.0, 3.0),
    CommonFood("Poha", "1 plate (150 g)", 250, 5.0, 40.0, 8.0),
    CommonFood("Upma", "1 plate (150 g)", 230, 6.0, 35.0, 7.0),
    CommonFood("Idli", "2 pieces", 130, 4.0, 26.0, 0.5),
    CommonFood("Dosa", "1 plain", 170, 4.0, 28.0, 4.0),
    CommonFood("Moong sprouts", "1 bowl (100 g)", 100, 7.0, 17.0, 0.5),
    CommonFood("Boiled potato", "1 medium (150 g)", 130, 3.0, 30.0, 0.2),
    CommonFood("Bread", "1 slice", 80, 3.0, 14.0, 1.0),
    CommonFood("Peanut butter", "1 tbsp (16 g)", 95, 4.0, 3.0, 8.0),
    CommonFood("Almonds", "10 nuts (12 g)", 70, 2.5, 2.5, 6.0),
    CommonFood("Peanuts, roasted", "30 g", 170, 7.0, 5.0, 14.0),
    CommonFood("Banana", "1 medium", 105, 1.3, 27.0, 0.4),
    CommonFood("Apple", "1 medium", 95, 0.5, 25.0, 0.3),
    CommonFood("Samosa", "1 piece", 260, 4.0, 28.0, 15.0),
    CommonFood("Pizza", "1 slice", 270, 11.0, 33.0, 10.0),
    CommonFood("Gulab jamun", "1 piece", 150, 2.0, 22.0, 6.0),
    CommonFood("Tea with milk and sugar", "1 cup", 70, 2.0, 11.0, 2.0),
    CommonFood("Soft drink", "1 can (330 ml)", 140, 0.0, 35.0, 0.0),
)

/** The common foods whose name contains every word of [query], best matches first; none for a query under two letters. */
fun searchCommonFoods(query: String, limit: Int = 6): List<CommonFood> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty() || query.trim().length < 2) return emptyList()
    return CommonFoods
        .filter { food -> words.all { it in food.name.lowercase() } }
        .sortedBy { food -> if (food.name.lowercase().startsWith(words.first())) 0 else 1 }
        .take(limit)
}
