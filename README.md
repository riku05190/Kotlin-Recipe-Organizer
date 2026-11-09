# Overview

As a software engineer expanding my proficiency in modern JVM languages, I built the **Kotlin Recipe Organizer** to explore expressive object-oriented design, null safety, and functional collection processing in Kotlin.

The **Kotlin Recipe Organizer** is a dual-interface (Console CLI and built-in Web UI) application that lets users manage recipes, ingredients, and cooking steps. Users can browse recipes, filter by category, search by title or ingredient keywords, dynamically scale ingredient amounts for any number of servings, toggle favorite recipes, and view category statistics. Both the interactive terminal menu and the web dashboard (`http://localhost:8080`) share the same core Kotlin domain model.

My purpose in writing this software was to gain hands-on experience with core Kotlin language constructs—including `val` and `var` variables, `when` expressions, loops, modular functions with default and named parameters, classes, data classes (`copy()`), and mutable/immutable collections (`mutableListOf`, `mutableMapOf`, `filter`, `map`, `any`, `count`).

[Software Demo Video](https://youtu.be/enRZ66SA1Ek)

# Development Environment

- **IDE & Build Tools**: IntelliJ IDEA, Kotlin Command-Line Compiler (`kotlinc`), and OpenJDK 21
- **Operating System**: macOS

- **Programming Language**: Kotlin (JVM target)
- **Libraries**: Kotlin Standard Library (`kotlin.collections`, `kotlin.text`) and the JDK built-in HTTP server (`com.sun.net.httpserver.HttpServer`) for serving the web interface without external framework dependencies.

# Useful Websites

- [Kotlin Official Documentation](https://kotlinlang.org/docs/home.html)
- [Kotlin Data Classes Guide](https://kotlinlang.org/docs/data-classes.html)
- [Kotlin Collections Overview](https://kotlinlang.org/docs/collections-overview.html)
- [Kotlin Control Flow: Conditions and Loops](https://kotlinlang.org/docs/control-flow.html)

# Future Work

- Add persistent storage using SQLite or JSON file serialization so recipes are saved across application restarts.
- Support unit conversion (e.g., converting between grams, ounces, and cups) when scaling recipes.
- Add nutritional calculation per serving and shopping list export functionality.
