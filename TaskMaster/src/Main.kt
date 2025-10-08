import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// =========================
// Data Layer
// =========================

data class Task(
    val id: Int,
    var title: String,
    var description: String?,
    var priority: String,
    var dueDate: String,
    var isCompleted: Boolean,
    var category: String,
    val createdAt: String
)

private val validPriorities: List<String> = listOf(
    "Низкий 🔵",
    "Средний 🟡",
    "Высокий 🟠",
    "Срочный 🔴"
)

private val validCategoriesDefault: List<String> = listOf(
    "Работа", "Личное", "Учеба", "Здоровье", "Финансы"
)

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private var nextId: Int = 1
private val tasks: MutableList<Task> = mutableListOf()
private val categories: MutableSet<String> = validCategoriesDefault.toMutableSet()

// Helpers
private fun todayString(): String = LocalDate.now().format(dateFmt)

private fun parseDateOrNull(text: String): LocalDate? = try {
    LocalDate.parse(text, dateFmt)
} catch (_: DateTimeParseException) { null }

// =========================
// Validation
// =========================

private fun isValidTitle(title: String): Boolean = title.isNotBlank()

private fun isValidPriority(priority: String): Boolean = validPriorities.contains(priority)

private fun isValidDueDate(due: String): Boolean = parseDateOrNull(due) != null

private fun ensureCategoryExists(cat: String): String {
    if (cat.isBlank()) return "Без категории"
    categories.add(cat)
    return cat
}

// =========================
// Business Logic
// =========================

private fun createTask(
    title: String,
    description: String?,
    priority: String,
    dueDate: String,
    category: String
): Result<Task> {
    if (!isValidTitle(title)) return Result.failure(IllegalArgumentException("Название не может быть пустым"))
    if (!isValidPriority(priority)) return Result.failure(IllegalArgumentException("Некорректный приоритет"))
    if (!isValidDueDate(dueDate)) return Result.failure(IllegalArgumentException("Некорректная дата (dd.MM.yyyy)"))

    val created = Task(
        id = nextId++,
        title = title.trim(),
        description = description?.ifBlank { null },
        priority = priority,
        dueDate = dueDate,
        isCompleted = false,
        category = ensureCategoryExists(category.trim()),
        createdAt = todayString()
    )
    tasks.add(created)
    return Result.success(created)
}

private fun listTasks(): List<Task> = tasks.toList()

private fun findTaskById(id: Int): Task? = tasks.firstOrNull { it.id == id }

private fun updateTask(
    id: Int,
    title: String?,
    description: String?,
    priority: String?,
    dueDate: String?,
    category: String?
): Result<Task> {
    val task = findTaskById(id) ?: return Result.failure(NoSuchElementException("Задача не найдена"))
    if (task.isCompleted) return Result.failure(IllegalStateException("Нельзя редактировать выполненную задачу"))

    title?.let {
        if (!isValidTitle(it)) return Result.failure(IllegalArgumentException("Название не может быть пустым"))
        task.title = it.trim()
    }
    if (description != null) task.description = description.ifBlank { null }
    priority?.let {
        if (!isValidPriority(it)) return Result.failure(IllegalArgumentException("Некорректный приоритет"))
        task.priority = it
    }
    dueDate?.let {
        if (!isValidDueDate(it)) return Result.failure(IllegalArgumentException("Некорректная дата (dd.MM.yyyy)"))
        task.dueDate = it
    }
    category?.let { task.category = ensureCategoryExists(it.trim()) }
    return Result.success(task)
}

private fun deleteTask(id: Int, confirm: Boolean): Result<Unit> {
    if (!confirm) return Result.failure(IllegalStateException("Удаление не подтверждено"))
    val removed = tasks.removeIf { it.id == id }
    return if (removed) Result.success(Unit) else Result.failure(NoSuchElementException("Задача не найдена"))
}

private fun markCompleted(id: Int): Result<Task> {
    val task = findTaskById(id) ?: return Result.failure(NoSuchElementException("Задача не найдена"))
    task.isCompleted = true
    return Result.success(task)
}

// Filters & Search
private enum class StatusFilter { ALL, ACTIVE, DONE }

private fun filterByStatus(status: StatusFilter): List<Task> = when (status) {
    StatusFilter.ALL -> tasks
    StatusFilter.ACTIVE -> tasks.filter { !it.isCompleted }
    StatusFilter.DONE -> tasks.filter { it.isCompleted }
}

private fun search(text: String): List<Task> {
    if (text.isBlank()) return tasks
    val q = text.trim().lowercase(Locale.getDefault())
    return tasks.filter { t ->
        t.title.lowercase(Locale.getDefault()).contains(q) ||
                (t.description?.lowercase(Locale.getDefault())?.contains(q) == true)
    }
}

private fun filterByCategory(cat: String): List<Task> = tasks.filter { it.category.equals(cat, ignoreCase = true) }

private fun filterByPriority(p: String): List<Task> = tasks.filter { it.priority == p }

private fun overdue(): List<Task> {
    val today = LocalDate.now()
    return tasks.filter { !it.isCompleted && (parseDateOrNull(it.dueDate)?.isBefore(today) == true) }
}

// Analytics
private data class Stats(
    val total: Int,
    val done: Int,
    val active: Int,
    val completionPercent: Int,
    val byPriority: Map<String, Int>,
    val byCategory: Map<String, Int>,
    val overdueCount: Int
)

private fun computeStats(): Stats {
    val total = tasks.size
    val done = tasks.count { it.isCompleted }
    val active = total - done
    val completionPercent = if (total == 0) 0 else (done * 100 / total)
    val byPriority = validPriorities.associateWith { p -> tasks.count { it.priority == p } }
    val byCategory = categories.associateWith { c -> tasks.count { it.category.equals(c, ignoreCase = true) } }
    val overdueCount = overdue().size
    return Stats(total, done, active, completionPercent, byPriority, byCategory, overdueCount)
}

// =========================
// Presentation Layer
// =========================

private fun printHeader(title: String) {
    println("\n=== $title ===")
}

private fun formatTask(t: Task): String {
    val statusEmoji = if (t.isCompleted) "✅" else "🟩"
    val idStr = "#" + t.id.toString().padStart(4, '0')
    val title = t.title
    val pr = t.priority
    val cat = t.category
    val due = t.dueDate
    val created = t.createdAt
    val desc = t.description ?: "—"
    return "$statusEmoji $idStr | $title | $pr | $cat | до $due | создано $created | $desc"
}

private fun printTasks(items: List<Task>) {
    if (items.isEmpty()) {
        println("(пусто)")
        return
    }
    println("Статус  | ID   | Название | Приоритет | Категория | Срок | Создано | Описание")
    println("-".repeat(100))
    items.forEach { println(formatTask(it)) }
}

private fun prompt(text: String): String {
    print("$text: ")
    return readLine()?.trim().orEmpty()
}

private fun promptYesNo(text: String): Boolean {
    val ans = prompt("$text (y/n)").lowercase(Locale.getDefault())
    return ans == "y" || ans == "yes" || ans == "д" || ans == "да"
}

private fun chooseFromList(title: String, options: List<String>): String {
    println(title)
    options.forEachIndexed { idx, s -> println("${idx + 1}. $s") }
    val idx = prompt("Выберите номер").toIntOrNull()
    return if (idx != null && idx in 1..options.size) options[idx - 1] else options.first()
}

private fun menu() {
    while (true) {
        println()
        println("TaskMaster — Система управления задачами")
        println("1. Добавить задачу")
        println("2. Список задач")
        println("3. Редактировать задачу")
        println("4. Удалить задачу")
        println("5. Отметить выполненной")
        println("6. Поиск / Фильтры")
        println("7. Аналитика")
        println("0. Выход")

        when (prompt("Ваш выбор").toIntOrNull()) {
            1 -> uiAddTask()
            2 -> uiListTasks()
            3 -> uiEditTask()
            4 -> uiDeleteTask()
            5 -> uiCompleteTask()
            6 -> uiSearchFilters()
            7 -> uiAnalytics()
            0 -> return
            else -> println("Некорректный выбор")
        }
    }
}

private fun uiAddTask() {
    printHeader("Добавление задачи")
    val title = prompt("Название")
    val description = prompt("Описание (опционально)").ifBlank { null }
    val priority = chooseFromList("Приоритет", validPriorities)
    val catInput = prompt("Категория (существующая или новая)")
    val due = prompt("Дата выполнения (dd.MM.yyyy)")
    val res = createTask(title, description, priority, due, catInput)
    res.onSuccess { println("Создано: ${formatTask(it)}") }
        .onFailure { println("Ошибка: ${it.message}") }
}

private fun uiListTasks() {
    printHeader("Все задачи")
    val grouped = listTasks().groupBy { it.category }
    if (grouped.isEmpty()) {
        println("Задач нет")
        return
    }
    grouped.keys.sorted().forEach { cat ->
        println("\n— Категория: $cat —")
        printTasks(grouped[cat]!!.sortedBy { it.id })
    }
}

private fun uiEditTask() {
    printHeader("Редактирование задачи")
    val id = prompt("ID задачи").toIntOrNull()
    if (id == null) {
        println("Некорректный ID")
        return
    }
    val title = prompt("Новое название (пусто — оставить)").ifBlank { null }
    val descriptionRaw = prompt("Новое описание (пусто — оставить, '-' — очистить)")
    val description = when {
        descriptionRaw == "-" -> ""
        descriptionRaw.isBlank() -> null
        else -> descriptionRaw
    }
    val changePriority = promptYesNo("Изменить приоритет?")
    val priority = if (changePriority) chooseFromList("Приоритет", validPriorities) else null
    val due = prompt("Новая дата (dd.MM.yyyy, пусто — оставить)").ifBlank { null }
    val cat = prompt("Новая категория (пусто — оставить)").ifBlank { null }
    val res = updateTask(id, title, description, priority, due, cat)
    res.onSuccess { println("Обновлено: ${formatTask(it)}") }
        .onFailure { println("Ошибка: ${it.message}") }
}

private fun uiDeleteTask() {
    printHeader("Удаление задачи")
    val id = prompt("ID задачи").toIntOrNull()
    if (id == null) {
        println("Некорректный ID")
        return
    }
    val confirmed = promptYesNo("Точно удалить?")
    val res = deleteTask(id, confirmed)
    res.onSuccess { println("Удалено") }
        .onFailure { println("Ошибка: ${it.message}") }
}

private fun uiCompleteTask() {
    printHeader("Отметить выполненной")
    val id = prompt("ID задачи").toIntOrNull()
    if (id == null) {
        println("Некорректный ID")
        return
    }
    val res = markCompleted(id)
    res.onSuccess { println("Выполнено: ${formatTask(it)}") }
        .onFailure { println("Ошибка: ${it.message}") }
}

private fun uiSearchFilters() {
    printHeader("Поиск и фильтры")
    println("1. Поиск по тексту")
    println("2. Фильтр по статусу")
    println("3. Фильтр по категории")
    println("4. Фильтр по приоритету")
    println("5. Просроченные задачи")
    when (prompt("Ваш выбор").toIntOrNull()) {
        1 -> {
            val q = prompt("Поиск по названию/описанию")
            printTasks(search(q))
        }
        2 -> {
            val s = chooseFromList("Статус", listOf("Все", "Активные", "Выполненные"))
            val status = when (s) {
                "Активные" -> StatusFilter.ACTIVE
                "Выполненные" -> StatusFilter.DONE
                else -> StatusFilter.ALL
            }
            printTasks(filterByStatus(status))
        }
        3 -> {
            val cat = prompt("Категория")
            printTasks(filterByCategory(cat))
        }
        4 -> {
            val p = chooseFromList("Приоритет", validPriorities)
            printTasks(filterByPriority(p))
        }
        5 -> printTasks(overdue())
        else -> println("Некорректный выбор")
    }
}

private fun uiAnalytics() {
    printHeader("Аналитика")
    val s = computeStats()
    println("Всего: ${s.total}")
    println("Выполнено: ${s.done}")
    println("Активные: ${s.active}")
    println("Процент выполнения: ${s.completionPercent}%")
    println("\nПо приоритетам:")
    s.byPriority.forEach { (p, c) -> println("$p: $c") }
    println("\nПо категориям:")
    s.byCategory.forEach { (c, n) -> println("$c: $n") }
    println("\nПросроченные: ${s.overdueCount}")
}

fun main() {

    menu()
}