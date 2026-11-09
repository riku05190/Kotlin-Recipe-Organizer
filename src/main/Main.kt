// --- Kotlin Recipe Organizer ---

// Data class for recipe ingredients
data class Ingredient(
    val name: String,
    val amount: Double,
    val unit: String
) {
    fun format(): String = "$name: ${formatAmount(amount)} $unit"
}

// Class representing a single recipe
class Recipe(
    val id: Int,
    var title: String,
    var category: String,
    var servings: Int = 2,
    var prepTimeMinutes: Int = 15,
    var isFavorite: Boolean = false,
    val ingredients: MutableList<Ingredient> = mutableListOf(),
    val steps: MutableList<String> = mutableListOf()
) {
    fun addIngredient(ingredient: Ingredient) {
        ingredients.add(ingredient)
    }

    fun addStep(stepDescription: String) {
        if (stepDescription.isNotBlank()) {
            steps.add(stepDescription.trim())
        }
    }

    // Determine difficulty based on prep time
    fun getDifficultyLabel(): String = when {
        prepTimeMinutes <= 15 -> "Easy"
        prepTimeMinutes in 16..40 -> "Medium"
        else -> "Hard"
    }

    // Scale ingredient amounts for target servings using data class copy()
    fun getScaledIngredients(targetServings: Int): List<Ingredient> {
        val ratio = if (servings > 0) targetServings.toDouble() / servings else 1.0
        return ingredients.map { item ->
            item.copy(amount = item.amount * ratio)
        }
    }

    fun displayRecipe(targetServings: Int = servings) {
        val favoriteBadge = if (isFavorite) "[Favorite]" else ""
        println("--------------------------------------------------")
        println("Recipe ID : $id $favoriteBadge")
        println("Title     : $title")
        println("Category  : $category")
        println("Prep Time : $prepTimeMinutes mins [${getDifficultyLabel()}]")
        println("Servings  : $targetServings ${if (targetServings != servings) "(Scaled from $servings)" else ""}")
        println("Ingredients:")

        val displayList = if (targetServings == servings) ingredients else getScaledIngredients(targetServings)
        if (displayList.isEmpty()) {
            println("  (No ingredients listed)")
        } else {
            for (item in displayList) {
                println("  - ${item.format()}")
            }
        }

        if (steps.isNotEmpty()) {
            println("Steps:")
            for ((index, step) in steps.withIndex()) {
                println("  ${index + 1}. $step")
            }
        }
        println("--------------------------------------------------")
    }
}

// Class managing the collection of recipes
class RecipeOrganizer {
    private val recipeBook: MutableList<Recipe> = mutableListOf()
    private var nextId: Int = 1

    fun addRecipe(
        title: String,
        category: String,
        servings: Int = 2,
        prepTimeMinutes: Int = 15,
        isFavorite: Boolean = false
    ): Recipe {
        val recipe = Recipe(
            id = nextId++,
            title = title,
            category = category,
            servings = servings,
            prepTimeMinutes = prepTimeMinutes,
            isFavorite = isFavorite
        )
        recipeBook.add(recipe)
        return recipe
    }

    fun getAllRecipes(): List<Recipe> = recipeBook.toList()

    fun findById(id: Int): Recipe? = recipeBook.find { it.id == id }

    fun searchByCategory(category: String): List<Recipe> {
        return recipeBook.filter { it.category.equals(category.trim(), ignoreCase = true) }
    }

    fun searchByKeyword(keyword: String): List<Recipe> {
        val query = keyword.trim()
        return recipeBook.filter { recipe ->
            recipe.title.contains(query, ignoreCase = true) ||
                recipe.ingredients.any { ing -> ing.name.contains(query, ignoreCase = true) }
        }
    }

    fun removeRecipeById(id: Int): Boolean {
        val target = findById(id) ?: return false
        return recipeBook.remove(target)
    }

    // Count recipes per category using a mutable map
    fun getCategoryCounts(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (recipe in recipeBook) {
            val currentCount = counts[recipe.category] ?: 0
            counts[recipe.category] = currentCount + 1
        }
        return counts
    }
}

// --- Helper Functions ---

fun formatAmount(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format("%.1f", value)
    }
}

fun seedSampleRecipes(organizer: RecipeOrganizer) {
    val recipe1 = organizer.addRecipe(
        title = "Chocolate Chip Cookies",
        category = "Dessert",
        servings = 4,
        prepTimeMinutes = 25,
        isFavorite = true
    )
    recipe1.addIngredient(Ingredient("Flour", 200.0, "g"))
    recipe1.addIngredient(Ingredient("Sugar", 100.0, "g"))
    recipe1.addIngredient(Ingredient("Butter", 150.0, "g"))
    recipe1.addIngredient(Ingredient("Chocolate Chips", 120.0, "g"))
    recipe1.addStep("Preheat oven to 180C.")
    recipe1.addStep("Cream butter and sugar together, then mix in flour and chocolate chips.")
    recipe1.addStep("Bake on a tray for 12-15 minutes until golden brown.")

    val recipe2 = organizer.addRecipe(
        title = "Classic Pancakes",
        category = "Breakfast",
        servings = 2,
        prepTimeMinutes = 15,
        isFavorite = true
    )
    recipe2.addIngredient(Ingredient("Flour", 150.0, "g"))
    recipe2.addIngredient(Ingredient("Milk", 200.0, "ml"))
    recipe2.addIngredient(Ingredient("Egg", 1.0, "pcs"))
    recipe2.addIngredient(Ingredient("Butter", 20.0, "g"))
    recipe2.addStep("Whisk flour, milk, and egg in a bowl until smooth.")
    recipe2.addStep("Melt butter in a pan over medium heat and pour the batter.")
    recipe2.addStep("Flip when bubbles form and cook until golden on both sides.")

    val recipe3 = organizer.addRecipe(
        title = "Garlic Teriyaki Chicken",
        category = "Dinner",
        servings = 2,
        prepTimeMinutes = 30,
        isFavorite = false
    )
    recipe3.addIngredient(Ingredient("Chicken Thigh", 300.0, "g"))
    recipe3.addIngredient(Ingredient("Soy Sauce", 30.0, "ml"))
    recipe3.addIngredient(Ingredient("Mirin", 30.0, "ml"))
    recipe3.addIngredient(Ingredient("Garlic", 2.0, "cloves"))
    recipe3.addStep("Sear sliced garlic and chicken thighs skin-side down in a skillet.")
    recipe3.addStep("Add soy sauce and mirin, simmering until the glaze thickens.")
}

fun handleListAllRecipes(organizer: RecipeOrganizer) {
    println("\n--- All Recipes ---")
    val recipes = organizer.getAllRecipes()
    if (recipes.isEmpty()) {
        println("No recipes available.")
    } else {
        for (recipe in recipes) {
            recipe.displayRecipe()
        }
    }
}

fun handleSearchByCategory(organizer: RecipeOrganizer) {
    print("Enter category to search (e.g., Dessert, Breakfast, Dinner): ")
    val searchCategory = readLine()?.trim() ?: ""
    if (searchCategory.isEmpty()) {
        println("Category name cannot be empty.")
        return
    }

    val filtered = organizer.searchByCategory(searchCategory)
    println("\n--- Search Results for Category '$searchCategory' ---")
    if (filtered.isEmpty()) {
        println("No recipes found in this category.")
    } else {
        for (recipe in filtered) {
            recipe.displayRecipe()
        }
    }
}

fun handleSearchByKeyword(organizer: RecipeOrganizer) {
    print("Enter recipe title or ingredient name to search (e.g., Flour, Chicken): ")
    val keyword = readLine()?.trim() ?: ""
    if (keyword.isEmpty()) {
        println("Search keyword cannot be empty.")
        return
    }

    val results = organizer.searchByKeyword(keyword)
    println("\n--- Search Results for Keyword '$keyword' ---")
    if (results.isEmpty()) {
        println("No matching recipes found.")
    } else {
        for (recipe in results) {
            recipe.displayRecipe()
        }
    }
}

fun handleAddNewRecipe(organizer: RecipeOrganizer) {
    println("\n--- Add New Recipe ---")
    print("Enter Recipe Title: ")
    val rawTitle = readLine()?.trim() ?: ""
    val title = if (rawTitle.isNotEmpty()) rawTitle else "Untitled"

    print("Enter Category (e.g., Dessert, Breakfast, Dinner): ")
    val rawCategory = readLine()?.trim() ?: ""
    val category = if (rawCategory.isNotEmpty()) rawCategory else "General"

    print("Enter Servings (default 2): ")
    val servingsInput = readLine()?.trim()?.toIntOrNull()
    val servings = if (servingsInput != null && servingsInput > 0) servingsInput else 2

    print("Enter Prep Time in minutes (default 15): ")
    val prepTimeInput = readLine()?.trim()?.toIntOrNull()
    val prepTime = if (prepTimeInput != null && prepTimeInput > 0) prepTimeInput else 15

    val newRecipe = organizer.addRecipe(
        title = title,
        category = category,
        servings = servings,
        prepTimeMinutes = prepTime
    )

    // Loop to add ingredients
    var addingIngredients = true
    while (addingIngredients) {
        print("Add an ingredient? (y/n): ")
        val choice = readLine()?.trim() ?: "n"
        if (choice.equals("y", ignoreCase = true)) {
            print("  Ingredient Name: ")
            val ingNameInput = readLine()?.trim() ?: ""
            val ingName = if (ingNameInput.isNotEmpty()) ingNameInput else "Item"

            print("  Amount (number): ")
            val amountInput = readLine()?.trim()?.toDoubleOrNull()
            val amount = if (amountInput != null && amountInput > 0.0) amountInput else 1.0

            print("  Unit (g, ml, pcs, etc.): ")
            val unitInput = readLine()?.trim() ?: ""
            val unit = if (unitInput.isNotEmpty()) unitInput else "units"

            newRecipe.addIngredient(Ingredient(ingName, amount, unit))
        } else {
            addingIngredients = false
        }
    }

    // Loop to add cooking steps
    var addingSteps = true
    while (addingSteps) {
        print("Add a cooking step? (y/n): ")
        val choice = readLine()?.trim() ?: "n"
        if (choice.equals("y", ignoreCase = true)) {
            print("  Step ${newRecipe.steps.size + 1} description: ")
            val stepText = readLine()?.trim() ?: ""
            if (stepText.isNotEmpty()) {
                newRecipe.addStep(stepText)
            }
        } else {
            addingSteps = false
        }
    }

    println("Recipe '${newRecipe.title}' (ID: ${newRecipe.id}) added successfully!")
}

fun handleScaleRecipe(organizer: RecipeOrganizer) {
    print("Enter Recipe ID to scale servings: ")
    val id = readLine()?.trim()?.toIntOrNull()
    if (id == null) {
        println("Invalid ID. Please enter a valid number.")
        return
    }

    val recipe = organizer.findById(id)
    if (recipe == null) {
        println("Recipe with ID $id not found.")
        return
    }

    print("Enter desired number of servings (current: ${recipe.servings}): ")
    val targetServings = readLine()?.trim()?.toIntOrNull()
    if (targetServings == null || targetServings <= 0) {
        println("Invalid servings number. Must be greater than 0.")
        return
    }

    println("\n--- Scaled Recipe Preview ---")
    recipe.displayRecipe(targetServings = targetServings)
}

fun handleToggleFavorite(organizer: RecipeOrganizer) {
    print("Enter Recipe ID to toggle favorite status: ")
    val id = readLine()?.trim()?.toIntOrNull()
    if (id == null) {
        println("Invalid ID. Please enter a valid number.")
        return
    }

    val recipe = organizer.findById(id)
    if (recipe == null) {
        println("Recipe with ID $id not found.")
        return
    }

    recipe.isFavorite = !recipe.isFavorite
    val statusText = if (recipe.isFavorite) "added to Favorites" else "removed from Favorites"
    println("Recipe '${recipe.title}' is now $statusText.")
}

fun handleDeleteRecipe(organizer: RecipeOrganizer) {
    print("Enter Recipe ID to delete: ")
    val id = readLine()?.trim()?.toIntOrNull()
    if (id == null) {
        println("Invalid ID. Please enter a valid number.")
        return
    }

    val removed = organizer.removeRecipeById(id)
    if (removed) {
        println("Recipe (ID: $id) was deleted successfully.")
    } else {
        println("Recipe with ID $id not found.")
    }
}

fun handleShowStatistics(organizer: RecipeOrganizer) {
    println("\n--- Recipe Organizer Statistics ---")
    val recipes = organizer.getAllRecipes()
    println("Total Recipes : ${recipes.size}")
    val favoriteCount = recipes.count { it.isFavorite }
    println("Favorites     : $favoriteCount")

    val categoryCounts = organizer.getCategoryCounts()
    if (categoryCounts.isEmpty()) {
        println("No categories recorded yet.")
    } else {
        println("Recipes per Category:")
        for ((category, count) in categoryCounts) {
            println("  - $category: $count recipe(s)")
        }
    }
}

// --- Main Entry Point ---

fun main(args: Array<String>) {
    val organizer = RecipeOrganizer()
    seedSampleRecipes(organizer)

    // Start built-in web server on http://localhost:8080
    val webServer = RecipeWebServer(organizer, port = 8080)
    try {
        webServer.start()
    } catch (e: Exception) {
        println("Note: Web UI could not bind to port 8080 (${e.message}).")
    }

    // Keep running in web-only mode if "--web" flag is passed
    if (args.contains("--web")) {
        println("Running in Web Server mode. Press Ctrl+C to stop.")
        Thread.currentThread().join()
        return
    }

    var running = true

    while (running) {
        println("\n=== Kotlin Recipe Organizer (Web: http://localhost:8080) ===")
        println("1. List All Recipes")
        println("2. Search Recipes by Category")
        println("3. Search Recipes by Ingredient / Title")
        println("4. Add New Recipe")
        println("5. Scale Recipe Servings")
        println("6. Toggle Favorite")
        println("7. Delete Recipe")
        println("8. Show Category Statistics")
        println("9. Exit")
        print("Choose an option (1-9): ")

        val input = readLine() ?: break
        val trimmedInput = input.trim()

        when (trimmedInput) {
            "1" -> handleListAllRecipes(organizer)
            "2" -> handleSearchByCategory(organizer)
            "3" -> handleSearchByKeyword(organizer)
            "4" -> handleAddNewRecipe(organizer)
            "5" -> handleScaleRecipe(organizer)
            "6" -> handleToggleFavorite(organizer)
            "7" -> handleDeleteRecipe(organizer)
            "8" -> handleShowStatistics(organizer)
            "9", "exit", "quit", "q" -> {
                println("Exiting Recipe Organizer. Goodbye!")
                running = false
            }
            else -> {
                println("Invalid option. Please enter a number between 1 and 9.")
            }
        }
    }

    webServer.stop()
}
