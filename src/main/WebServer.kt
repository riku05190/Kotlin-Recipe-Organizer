import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

// Simple HTTP server written in Kotlin to expose RecipeOrganizer on the web
class RecipeWebServer(
    private val organizer: RecipeOrganizer,
    private val port: Int = 8080
) {
    private var server: HttpServer? = null

    fun start() {
        val httpServer = HttpServer.create(InetSocketAddress(port), 0)

        httpServer.createContext("/") { exchange ->
            try {
                handleRequest(exchange)
            } catch (e: Exception) {
                sendHtml(exchange, 500, "<h1>500 Internal Server Error</h1><pre>${escapeHtml(e.message ?: "")}</pre>")
            }
        }

        httpServer.executor = null
        httpServer.start()
        server = httpServer
        println("Web UI is running at: http://localhost:$port")
    }

    fun stop() {
        server?.stop(0)
    }

    private fun handleRequest(exchange: HttpExchange) {
        val method = exchange.requestMethod
        val path = exchange.requestURI.path
        val queryParams = parseQuery(exchange.requestURI.rawQuery)

        // Route requests using Kotlin's when expression
        when {
            method == "GET" && path == "/" -> {
                val categoryFilter = queryParams["category"] ?: ""
                val keywordFilter = queryParams["q"] ?: ""
                val scaleId = queryParams["scaleId"]?.toIntOrNull()
                val scaleServings = queryParams["servings"]?.toIntOrNull()

                val html = renderDashboardHtml(
                    categoryFilter = categoryFilter,
                    keywordFilter = keywordFilter,
                    scaleId = scaleId,
                    scaleServings = scaleServings
                )
                sendHtml(exchange, 200, html)
            }

            method == "POST" && path == "/add" -> {
                val form = parseFormBody(exchange)
                val title = form["title"]?.takeIf { it.isNotBlank() } ?: "Untitled"
                val category = form["category"]?.takeIf { it.isNotBlank() } ?: "General"
                val servings = form["servings"]?.toIntOrNull()?.takeIf { it > 0 } ?: 2
                val prepTime = form["prepTime"]?.toIntOrNull()?.takeIf { it > 0 } ?: 15

                val newRecipe = organizer.addRecipe(
                    title = title,
                    category = category,
                    servings = servings,
                    prepTimeMinutes = prepTime
                )

                // Parse ingredients (one per line: Name, Amount, Unit)
                val rawIngredients = form["ingredients"] ?: ""
                for (line in rawIngredients.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        val parts = trimmed.split(",").map { it.trim() }
                        val ingName = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: "Item"
                        val amount = parts.getOrNull(1)?.toDoubleOrNull() ?: 1.0
                        val unit = parts.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: "units"
                        newRecipe.addIngredient(Ingredient(ingName, amount, unit))
                    }
                }

                // Parse steps (one per line)
                val rawSteps = form["steps"] ?: ""
                for (line in rawSteps.lines()) {
                    if (line.isNotBlank()) {
                        newRecipe.addStep(line)
                    }
                }

                redirect(exchange, "/")
            }

            method == "POST" && path == "/favorite" -> {
                val form = parseFormBody(exchange)
                val id = form["id"]?.toIntOrNull()
                if (id != null) {
                    val recipe = organizer.findById(id)
                    if (recipe != null) {
                        recipe.isFavorite = !recipe.isFavorite
                    }
                }
                redirect(exchange, "/")
            }

            method == "POST" && path == "/delete" -> {
                val form = parseFormBody(exchange)
                val id = form["id"]?.toIntOrNull()
                if (id != null) {
                    organizer.removeRecipeById(id)
                }
                redirect(exchange, "/")
            }

            else -> {
                sendHtml(exchange, 404, "<h1>404 Not Found</h1>")
            }
        }
    }

    private fun renderDashboardHtml(
        categoryFilter: String,
        keywordFilter: String,
        scaleId: Int?,
        scaleServings: Int?
    ): String {
        val allRecipes = organizer.getAllRecipes()
        val categoryCounts = organizer.getCategoryCounts()
        val favoriteCount = allRecipes.count { it.isFavorite }

        val filteredRecipes = when {
            categoryFilter.isNotBlank() -> organizer.searchByCategory(categoryFilter)
            keywordFilter.isNotBlank() -> organizer.searchByKeyword(keywordFilter)
            else -> allRecipes
        }

        val categoryPillsHtml = buildString {
            val allActive = if (categoryFilter.isEmpty() && keywordFilter.isEmpty()) "active" else ""
            append("""<a href="/" class="pill $allActive">All (${allRecipes.size})</a>""")
            for ((cat, count) in categoryCounts) {
                val active = if (cat.equals(categoryFilter, ignoreCase = true)) "active" else ""
                append("""<a href="/?category=${encodeUrl(cat)}" class="pill $active">${escapeHtml(cat)} ($count)</a>""")
            }
        }

        val recipesHtml = if (filteredRecipes.isEmpty()) {
            """
            <div class="empty-state">
                <h3>No matching recipes found</h3>
                <p>Try clearing the filter or adding a new recipe.</p>
                <a href="/" class="btn btn-secondary">Reset Filters</a>
            </div>
            """.trimIndent()
        } else {
            filteredRecipes.joinToString("\n") { recipe ->
                val targetServings = if (scaleId == recipe.id && scaleServings != null && scaleServings > 0) {
                    scaleServings
                } else {
                    recipe.servings
                }
                val displayIngredients = if (targetServings == recipe.servings) {
                    recipe.ingredients
                } else {
                    recipe.getScaledIngredients(targetServings)
                }
                val difficulty = recipe.getDifficultyLabel()
                val diffClass = difficulty.lowercase()

                val ingredientsListHtml = if (displayIngredients.isEmpty()) {
                    "<li><em>No ingredients listed</em></li>"
                } else {
                    displayIngredients.joinToString("") { ing ->
                        "<li><span>${escapeHtml(ing.name)}</span><strong>${formatAmount(ing.amount)} ${escapeHtml(ing.unit)}</strong></li>"
                    }
                }

                val stepsListHtml = if (recipe.steps.isEmpty()) {
                    "<p class=\"muted\">No steps listed.</p>"
                } else {
                    "<ol class=\"steps-list\">" + recipe.steps.joinToString("") { step ->
                        "<li>${escapeHtml(step)}</li>"
                    } + "</ol>"
                }

                val favActive = if (recipe.isFavorite) "fav-active" else ""
                val favIcon = if (recipe.isFavorite) "★ Favorite" else "☆ Favorite"

                """
                <div class="recipe-card">
                    <div class="card-header">
                        <div>
                            <span class="badge badge-category">${escapeHtml(recipe.category)}</span>
                            <span class="badge badge-$diffClass">$difficulty (${recipe.prepTimeMinutes}m)</span>
                        </div>
                        <div class="card-actions">
                            <form method="POST" action="/favorite" class="inline-form">
                                <input type="hidden" name="id" value="${recipe.id}">
                                <button type="submit" class="btn-icon $favActive">$favIcon</button>
                            </form>
                            <form method="POST" action="/delete" class="inline-form">
                                <input type="hidden" name="id" value="${recipe.id}">
                                <button type="submit" class="btn-icon btn-danger" title="Delete Recipe">Delete</button>
                            </form>
                        </div>
                    </div>
                    <h3 class="recipe-title">#${recipe.id} ${escapeHtml(recipe.title)}</h3>

                    <div class="scaler-bar">
                        <span>Servings: <strong>$targetServings</strong> ${if (targetServings != recipe.servings) "(base: ${recipe.servings})" else ""}</span>
                        <form method="GET" action="/" class="scaler-form">
                            <input type="hidden" name="scaleId" value="${recipe.id}">
                            <input type="number" name="servings" min="1" max="50" value="$targetServings" class="input-small">
                            <button type="submit" class="btn btn-small">Scale</button>
                        </form>
                    </div>

                    <div class="recipe-section">
                        <h4>Ingredients</h4>
                        <ul class="ingredients-list">
                            $ingredientsListHtml
                        </ul>
                    </div>

                    <div class="recipe-section">
                        <h4>Steps</h4>
                        $stepsListHtml
                    </div>
                </div>
                """.trimIndent()
            }
        }

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Kotlin Recipe Organizer</title>
            <style>
                :root {
                    --bg: #f8fafc;
                    --surface: #ffffff;
                    --text: #0f172a;
                    --muted: #64748b;
                    --primary: #7c3aed;
                    --primary-hover: #6d28d9;
                    --border: #e2e8f0;
                    --easy: #10b981;
                    --medium: #f59e0b;
                    --hard: #ef4444;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    background: var(--bg);
                    color: var(--text);
                    line-height: 1.5;
                }
                header {
                    background: linear-gradient(135deg, #4f46e5, #7c3aed);
                    color: #fff;
                    padding: 2rem 1.5rem;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.08);
                }
                .header-inner {
                    max-width: 1200px;
                    margin: 0 auto;
                    display: flex;
                    flex-wrap: wrap;
                    justify-content: space-between;
                    align-items: center;
                    gap: 1rem;
                }
                .stats-row {
                    display: flex;
                    gap: 1rem;
                }
                .stat-box {
                    background: rgba(255,255,255,0.16);
                    padding: 0.6rem 1rem;
                    border-radius: 10px;
                    backdrop-filter: blur(6px);
                    text-align: center;
                }
                .stat-box strong {
                    display: block;
                    font-size: 1.3rem;
                }
                .stat-box span {
                    font-size: 0.8rem;
                    opacity: 0.9;
                }
                .container {
                    max-width: 1200px;
                    margin: 1.8rem auto;
                    padding: 0 1.5rem;
                    display: grid;
                    grid-template-columns: 1fr 350px;
                    gap: 2rem;
                }
                @media (max-width: 900px) {
                    .container { grid-template-columns: 1fr; }
                }
                .toolbar {
                    background: var(--surface);
                    padding: 1.2rem;
                    border-radius: 12px;
                    border: 1px solid var(--border);
                    margin-bottom: 1.5rem;
                    display: flex;
                    flex-direction: column;
                    gap: 1rem;
                }
                .search-form {
                    display: flex;
                    gap: 0.6rem;
                }
                .search-form input {
                    flex: 1;
                    padding: 0.65rem 0.9rem;
                    border: 1px solid var(--border);
                    border-radius: 8px;
                    font-size: 0.95rem;
                }
                .pills {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 0.5rem;
                }
                .pill {
                    text-decoration: none;
                    color: var(--text);
                    background: #f1f5f9;
                    padding: 0.4rem 0.85rem;
                    border-radius: 999px;
                    font-size: 0.85rem;
                    font-weight: 500;
                    transition: 0.15s;
                }
                .pill:hover, .pill.active {
                    background: var(--primary);
                    color: #fff;
                }
                .recipes-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
                    gap: 1.25rem;
                }
                .recipe-card {
                    background: var(--surface);
                    border: 1px solid var(--border);
                    border-radius: 12px;
                    padding: 1.25rem;
                    display: flex;
                    flex-direction: column;
                    gap: 1rem;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.02);
                }
                .card-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    gap: 0.5rem;
                }
                .badge {
                    display: inline-block;
                    padding: 0.2rem 0.6rem;
                    border-radius: 6px;
                    font-size: 0.75rem;
                    font-weight: 600;
                }
                .badge-category { background: #ede9fe; color: #5b21b6; }
                .badge-easy { background: #d1fae5; color: #065f46; }
                .badge-medium { background: #fef3c7; color: #92400e; }
                .badge-hard { background: #fee2e2; color: #991b1b; }
                .card-actions { display: flex; gap: 0.4rem; }
                .inline-form { display: inline; }
                .btn-icon {
                    border: 1px solid var(--border);
                    background: #fff;
                    padding: 0.25rem 0.55rem;
                    border-radius: 6px;
                    cursor: pointer;
                    font-size: 0.78rem;
                }
                .btn-icon.fav-active {
                    background: #fef9c3;
                    border-color: #facc15;
                    color: #854d0e;
                    font-weight: 600;
                }
                .btn-icon.btn-danger:hover {
                    background: #fee2e2;
                    border-color: #fca5a5;
                    color: #991b1b;
                }
                .recipe-title {
                    font-size: 1.2rem;
                    color: var(--text);
                }
                .scaler-bar {
                    background: #f8fafc;
                    padding: 0.6rem 0.8rem;
                    border-radius: 8px;
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    font-size: 0.85rem;
                }
                .scaler-form {
                    display: flex;
                    align-items: center;
                    gap: 0.4rem;
                }
                .input-small {
                    width: 56px;
                    padding: 0.25rem 0.4rem;
                    border: 1px solid var(--border);
                    border-radius: 6px;
                }
                .recipe-section h4 {
                    font-size: 0.85rem;
                    text-transform: uppercase;
                    letter-spacing: 0.04em;
                    color: var(--muted);
                    margin-bottom: 0.4rem;
                }
                .ingredients-list {
                    list-style: none;
                    font-size: 0.9rem;
                }
                .ingredients-list li {
                    display: flex;
                    justify-content: space-between;
                    padding: 0.25rem 0;
                    border-bottom: 1px dashed var(--border);
                }
                .steps-list {
                    padding-left: 1.2rem;
                    font-size: 0.9rem;
                    color: #334155;
                }
                .steps-list li { margin-bottom: 0.3rem; }
                .sidebar-card {
                    background: var(--surface);
                    border: 1px solid var(--border);
                    border-radius: 12px;
                    padding: 1.5rem;
                    position: sticky;
                    top: 1.5rem;
                }
                .sidebar-card h2 {
                    font-size: 1.2rem;
                    margin-bottom: 1rem;
                }
                .form-group {
                    margin-bottom: 0.9rem;
                }
                .form-group label {
                    display: block;
                    font-size: 0.85rem;
                    font-weight: 600;
                    margin-bottom: 0.3rem;
                }
                .form-group input, .form-group textarea {
                    width: 100%;
                    padding: 0.55rem 0.75rem;
                    border: 1px solid var(--border);
                    border-radius: 8px;
                    font-size: 0.9rem;
                    font-family: inherit;
                }
                .form-row {
                    display: grid;
                    grid-template-columns: 1fr 1fr;
                    gap: 0.75rem;
                }
                .btn {
                    background: var(--primary);
                    color: #fff;
                    border: none;
                    padding: 0.65rem 1.1rem;
                    border-radius: 8px;
                    font-weight: 600;
                    cursor: pointer;
                    text-decoration: none;
                    display: inline-block;
                    text-align: center;
                }
                .btn:hover { background: var(--primary-hover); }
                .btn-small { padding: 0.3rem 0.65rem; font-size: 0.8rem; }
                .btn-secondary { background: #64748b; }
                .btn-block { width: 100%; }
                .empty-state {
                    background: var(--surface);
                    border: 1px solid var(--border);
                    border-radius: 12px;
                    padding: 2.5rem;
                    text-align: center;
                }
                .empty-state p { color: var(--muted); margin: 0.5rem 0 1rem; }
            </style>
        </head>
        <body>
            <header>
                <div class="header-inner">
                    <div>
                        <h1>Kotlin Recipe Organizer</h1>
                        <p>Powered by Kotlin Data Classes, Collections &amp; HttpServer</p>
                    </div>
                    <div class="stats-row">
                        <div class="stat-box">
                            <strong>${allRecipes.size}</strong>
                            <span>Total Recipes</span>
                        </div>
                        <div class="stat-box">
                            <strong>$favoriteCount</strong>
                            <span>Favorites</span>
                        </div>
                        <div class="stat-box">
                            <strong>${categoryCounts.size}</strong>
                            <span>Categories</span>
                        </div>
                    </div>
                </div>
            </header>

            <main class="container">
                <section>
                    <div class="toolbar">
                        <form method="GET" action="/" class="search-form">
                            <input type="text" name="q" placeholder="Search by recipe title or ingredient (e.g., Flour, Chicken)..." value="${escapeHtml(keywordFilter)}">
                            <button type="submit" class="btn">Search</button>
                            ${if (keywordFilter.isNotEmpty() || categoryFilter.isNotEmpty()) """<a href="/" class="btn btn-secondary">Clear</a>""" else ""}
                        </form>
                        <div class="pills">
                            $categoryPillsHtml
                        </div>
                    </div>

                    <div class="recipes-grid">
                        $recipesHtml
                    </div>
                </section>

                <aside>
                    <div class="sidebar-card">
                        <h2>Add New Recipe</h2>
                        <form method="POST" action="/add">
                            <div class="form-group">
                                <label>Recipe Title</label>
                                <input type="text" name="title" placeholder="e.g., Matcha Pancakes" required>
                            </div>
                            <div class="form-group">
                                <label>Category</label>
                                <input type="text" name="category" placeholder="e.g., Breakfast, Dessert, Dinner" required>
                            </div>
                            <div class="form-row">
                                <div class="form-group">
                                    <label>Servings</label>
                                    <input type="number" name="servings" value="2" min="1" required>
                                </div>
                                <div class="form-group">
                                    <label>Prep Time (mins)</label>
                                    <input type="number" name="prepTime" value="15" min="1" required>
                                </div>
                            </div>
                            <div class="form-group">
                                <label>Ingredients (one per line: Name, Amount, Unit)</label>
                                <textarea name="ingredients" rows="4" placeholder="Flour, 150, g&#10;Milk, 200, ml&#10;Egg, 1, pcs"></textarea>
                            </div>
                            <div class="form-group">
                                <label>Cooking Steps (one per line)</label>
                                <textarea name="steps" rows="4" placeholder="Mix dry ingredients.&#10;Whisk in milk and egg.&#10;Cook on a warm skillet."></textarea>
                            </div>
                            <button type="submit" class="btn btn-block">Add Recipe</button>
                        </form>
                    </div>
                </aside>
            </main>
        </body>
        </html>
        """.trimIndent()
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (pair in rawQuery.split("&")) {
            val parts = pair.split("=", limit = 2)
            val key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8)
            val value = if (parts.size > 1) URLDecoder.decode(parts[1], StandardCharsets.UTF_8) else ""
            map[key] = value
        }
        return map
    }

    private fun parseFormBody(exchange: HttpExchange): Map<String, String> {
        val body = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).readText()
        return parseQuery(body)
    }

    private fun sendHtml(exchange: HttpExchange, statusCode: Int, html: String) {
        val bytes = html.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun redirect(exchange: HttpExchange, location: String) {
        exchange.responseHeaders.set("Location", location)
        exchange.sendResponseHeaders(303, -1)
        exchange.close()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun encodeUrl(text: String): String {
        return java.net.URLEncoder.encode(text, StandardCharsets.UTF_8)
    }
}

