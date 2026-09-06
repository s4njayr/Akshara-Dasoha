package com.aksharadasoha.ledger.domain

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

sealed class Expr {
    data class NumberLit(val value: Double) : Expr()
    data class Ident(val name: String) : Expr()
    data class Binary(val op: Char, val left: Expr, val right: Expr) : Expr()
    data class UnaryMinus(val inner: Expr) : Expr()
    data class Call(val name: String, val args: List<Expr>) : Expr()
}

class FormulaParseException(message: String) : IllegalArgumentException(message)

class FormulaParser(private val source: String) {
    private val text = source.trim()
    private var i = 0

    fun parse(): Expr {
        if (text.isEmpty()) throw FormulaParseException("Formula is empty")
        val expr = parseExpr()
        skip()
        if (i < text.length) throw FormulaParseException("Unexpected input at ${text.substring(i)}")
        return expr
    }

    private fun parseExpr(): Expr {
        var left = parseTerm()
        while (true) {
            skip()
            val op = peek()
            if (op == '+' || op == '-') {
                i++
                left = Expr.Binary(op, left, parseTerm())
            } else {
                break
            }
        }
        return left
    }

    private fun parseTerm(): Expr {
        var left = parseFactor()
        while (true) {
            skip()
            val op = peek()
            if (op == '*' || op == '/') {
                i++
                left = Expr.Binary(op, left, parseFactor())
            } else {
                break
            }
        }
        return left
    }

    private fun parseFactor(): Expr {
        skip()
        return when (peek()) {
            '-' -> {
                i++
                Expr.UnaryMinus(parseFactor())
            }
            '(' -> {
                i++
                val inner = parseExpr()
                skip()
                expect(')')
                inner
            }
            else -> parsePrimary()
        }
    }

    private fun parsePrimary(): Expr {
        skip()
        if (peek().isDigit() || peek() == '.') return parseNumber()
        if (peek().isLetter() || peek() == '_') {
            val name = parseIdent()
            skip()
            if (peek() == '(') {
                i++
                val args = mutableListOf<Expr>()
                skip()
                if (peek() != ')') {
                    args += parseExpr()
                    while (true) {
                        skip()
                        if (peek() != ',') break
                        i++
                        args += parseExpr()
                    }
                }
                skip()
                expect(')')
                return Expr.Call(name.uppercase(Locale.US), args)
            }
            return Expr.Ident(name)
        }
        throw FormulaParseException("Expected value at index $i")
    }

    private fun parseNumber(): Expr {
        val start = i
        while (peek().isDigit()) i++
        if (peek() == '.') {
            i++
            while (peek().isDigit()) i++
        }
        return Expr.NumberLit(text.substring(start, i).toDouble())
    }

    private fun parseIdent(): String {
        val start = i
        while (peek().isLetterOrDigit() || peek() == '_') i++
        return text.substring(start, i)
    }

    private fun expect(ch: Char) {
        if (peek() != ch) throw FormulaParseException("Expected '$ch' at index $i")
        i++
    }

    private fun peek(): Char = if (i < text.length) text[i] else '\u0000'

    private fun skip() {
        while (peek().isWhitespace()) i++
    }
}

object FormulaValidator {
    private val functions = setOf(
        "SUM", "PRODUCT", "IF", "ISNUMBER", "ISFIRST", "INPUT", "CARRY", "PREV", "WEEKDAY"
    )

    fun validate(formula: String, knownNames: Set<String>): List<String> {
        return try {
            val expr = FormulaParser(formula).parse()
            val errors = mutableListOf<String>()
            collect(expr, knownNames, errors)
            errors
        } catch (error: FormulaParseException) {
            listOf(error.message ?: "Invalid formula")
        }
    }

    fun referencedColumns(formula: String?, includeCarry: Boolean = true): Set<String> {
        if (formula.isNullOrBlank()) return emptySet()
        return try {
            val names = mutableSetOf<String>()
            walk(FormulaParser(formula).parse(), names, includeCarry)
            names
        } catch (_: FormulaParseException) {
            emptySet()
        }
    }

    fun hasCycle(columns: List<ColumnDef>): List<String> {
        val graph = columns.associate { column ->
            column.key to referencedColumns(column.formula, includeCarry = false).filter { ref ->
                columns.any { it.key == ref }
            }
        }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val cycles = mutableListOf<String>()
        fun dfs(node: String, path: MutableList<String>) {
            if (node in visited) return
            if (node in visiting) {
                cycles += "Circular reference: ${(path + node).joinToString(" -> ")}"
                return
            }
            visiting += node
            path += node
            graph[node].orEmpty().forEach { dfs(it, path) }
            path.removeAt(path.lastIndex)
            visiting -= node
            visited += node
        }
        graph.keys.forEach { dfs(it, mutableListOf()) }
        return cycles
    }

    private fun collect(expr: Expr, known: Set<String>, errors: MutableList<String>) {
        when (expr) {
            is Expr.Ident -> if (expr.name !in known) errors += "Unknown name '${expr.name}'"
            is Expr.UnaryMinus -> collect(expr.inner, known, errors)
            is Expr.Binary -> {
                collect(expr.left, known, errors)
                collect(expr.right, known, errors)
            }
            is Expr.Call -> {
                if (expr.name !in functions) errors += "Unknown function ${expr.name}"
                expr.args.forEach { collect(it, known, errors) }
                if (expr.name in setOf("CARRY", "PREV") && expr.args.size == 1) {
                    val arg = expr.args[0]
                    if (arg !is Expr.Ident) errors += "${expr.name} requires a column name"
                }
            }
            is Expr.NumberLit -> Unit
        }
    }

    private fun walk(expr: Expr, names: MutableSet<String>, includeCarry: Boolean) {
        when (expr) {
            is Expr.Ident -> names += expr.name
            is Expr.UnaryMinus -> walk(expr.inner, names, includeCarry)
            is Expr.Binary -> {
                walk(expr.left, names, includeCarry)
                walk(expr.right, names, includeCarry)
            }
            is Expr.Call -> {
                if (expr.name in setOf("CARRY", "PREV")) {
                    if (includeCarry && expr.args.firstOrNull() is Expr.Ident) {
                        names += (expr.args[0] as Expr.Ident).name
                    }
                    return
                }
                expr.args.forEach { walk(it, names, includeCarry) }
            }
            is Expr.NumberLit -> Unit
        }
    }
}

class FormulaEngine {
    fun requiredColumns(template: LedgerTemplate): Set<String> {
        val visible = template.columns.filter { it.visible }.map { it.key }.toMutableSet()
        if (visible.isEmpty()) visible += template.columns.map { it.key }
        val required = visible.toMutableSet()
        var changed = true
        while (changed) {
            changed = false
            template.columns.filter { it.key in required }.forEach { column ->
                FormulaValidator.referencedColumns(column.formula, includeCarry = true).forEach { ref ->
                    if (template.columns.any { it.key == ref } && required.add(ref)) changed = true
                }
            }
        }
        return required
    }

    fun compute(template: LedgerTemplate, rows: List<LedgerRow>): List<ComputedRow> {
        val cache = mutableMapOf<Pair<Int, String>, ComputedCell>()
        val needed = requiredColumns(template)
        return rows.mapIndexed { index, row ->
            val cells = linkedMapOf<String, ComputedCell>()
            template.columns.filter { it.key in needed }.forEach { column ->
                val computed = valueOf(template, rows, index, column, cache, mutableSetOf())
                val source = row.sourceValues[column.key]
                val differs = source != null && computed.number != null && kotlin.math.abs(source - computed.number) > 0.0005
                cells[column.key] = computed.copy(sourceNumber = source, auditDiffers = differs)
            }
            ComputedRow(row, cells)
        }
    }

    fun preview(template: LedgerTemplate, sample: LedgerRow = LedgerRow("preview", "preview", "2025-01-01", 1, emptyMap())): ComputedRow {
        return compute(template, listOf(sample)).first()
    }

    private fun valueOf(
        template: LedgerTemplate,
        rows: List<LedgerRow>,
        rowIndex: Int,
        column: ColumnDef,
        cache: MutableMap<Pair<Int, String>, ComputedCell>,
        visiting: MutableSet<Pair<Int, String>>,
    ): ComputedCell {
        val key = rowIndex to column.key
        cache[key]?.let { return it }
        if (key in visiting) {
            return ComputedCell(column.key, error = "Circular reference at ${column.key}").also { cache[key] = it }
        }
        visiting += key
        val result = try {
            evaluateColumn(template, rows, rowIndex, column, cache, visiting)
        } catch (error: Exception) {
            ComputedCell(column.key, error = error.message ?: "Calculation error")
        }
        visiting -= key
        cache[key] = result
        return result
    }

    private fun evaluateColumn(
        template: LedgerTemplate,
        rows: List<LedgerRow>,
        rowIndex: Int,
        column: ColumnDef,
        cache: MutableMap<Pair<Int, String>, ComputedCell>,
        visiting: MutableSet<Pair<Int, String>>,
    ): ComputedCell {
        val row = rows[rowIndex]
        when (column.role) {
            ColumnRole.DATE, ColumnRole.INPUT -> {
                if (column.valueType == ValueType.DATE) {
                    return ComputedCell(column.key, text = row.date)
                }
                if (column.valueType == ValueType.TEXT) {
                    return ComputedCell(column.key, text = row.textCells[column.key].orEmpty())
                }
                val stored = row.cells[column.key]
                return numberCell(column, stored ?: 0.0)
            }
            ColumnRole.OPENING, ColumnRole.COMPUTED -> Unit
        }
        if (column.valueType == ValueType.WEEKDAY) {
            val date = LocalDate.parse(row.date)
            return ComputedCell(column.key, text = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
        }
        val formula = column.formula
        if (formula.isNullOrBlank()) {
            return numberCell(column, row.cells[column.key] ?: 0.0)
        }
        val expr = FormulaParser(formula).parse()
        val value = eval(expr, template, rows, rowIndex, column, cache, visiting)
        return when (value) {
            is EvalValue.Number -> numberCell(column, value.value)
            is EvalValue.Text -> ComputedCell(column.key, text = value.value)
            is EvalValue.Error -> ComputedCell(column.key, error = value.message)
        }
    }

    private fun numberCell(column: ColumnDef, value: Double): ComputedCell {
        val warning = column.warnNegative && value < 0
        return ComputedCell(column.key, number = value, negativeWarning = warning)
    }

    private fun eval(
        expr: Expr,
        template: LedgerTemplate,
        rows: List<LedgerRow>,
        rowIndex: Int,
        column: ColumnDef,
        cache: MutableMap<Pair<Int, String>, ComputedCell>,
        visiting: MutableSet<Pair<Int, String>>,
    ): EvalValue {
        return when (expr) {
            is Expr.NumberLit -> EvalValue.Number(expr.value)
            is Expr.UnaryMinus -> eval(expr.inner, template, rows, rowIndex, column, cache, visiting).mapNumber { -it }
            is Expr.Binary -> {
                val left = eval(expr.left, template, rows, rowIndex, column, cache, visiting).numberOrError()
                val right = eval(expr.right, template, rows, rowIndex, column, cache, visiting).numberOrError()
                if (left is EvalValue.Error) return left
                if (right is EvalValue.Error) return right
                val a = (left as EvalValue.Number).value
                val b = (right as EvalValue.Number).value
                when (expr.op) {
                    '+' -> EvalValue.Number(a + b)
                    '-' -> EvalValue.Number(a - b)
                    '*' -> EvalValue.Number(a * b)
                    '/' -> if (b == 0.0) EvalValue.Error("Division by zero") else EvalValue.Number(a / b)
                    else -> EvalValue.Error("Unknown operator")
                }
            }
            is Expr.Ident -> resolveIdent(expr.name, template, rows, rowIndex, column, cache, visiting)
            is Expr.Call -> evalCall(expr, template, rows, rowIndex, column, cache, visiting)
        }
    }

    private fun resolveIdent(
        name: String,
        template: LedgerTemplate,
        rows: List<LedgerRow>,
        rowIndex: Int,
        column: ColumnDef,
        cache: MutableMap<Pair<Int, String>, ComputedCell>,
        visiting: MutableSet<Pair<Int, String>>,
    ): EvalValue {
        template.rates.firstOrNull { it.key == name }?.let { return EvalValue.Number(it.value) }
        val target = template.columns.firstOrNull { it.key == name }
            ?: return EvalValue.Error("Unknown name '$name'")
        val cell = valueOf(template, rows, rowIndex, target, cache, visiting)
        return cell.toValue()
    }

    private fun evalCall(
        call: Expr.Call,
        template: LedgerTemplate,
        rows: List<LedgerRow>,
        rowIndex: Int,
        column: ColumnDef,
        cache: MutableMap<Pair<Int, String>, ComputedCell>,
        visiting: MutableSet<Pair<Int, String>>,
    ): EvalValue {
        fun arg(index: Int) = eval(call.args[index], template, rows, rowIndex, column, cache, visiting)
        return when (call.name) {
            "SUM" -> {
                var total = 0.0
                call.args.forEach {
                    when (val value = eval(it, template, rows, rowIndex, column, cache, visiting)) {
                        is EvalValue.Number -> total += value.value
                        is EvalValue.Error -> return value
                        is EvalValue.Text -> return EvalValue.Error("SUM requires numbers")
                    }
                }
                EvalValue.Number(total)
            }
            "PRODUCT" -> {
                var total = 1.0
                call.args.forEach {
                    when (val value = eval(it, template, rows, rowIndex, column, cache, visiting)) {
                        is EvalValue.Number -> total *= value.value
                        is EvalValue.Error -> return value
                        is EvalValue.Text -> return EvalValue.Error("PRODUCT requires numbers")
                    }
                }
                EvalValue.Number(total)
            }
            "IF" -> {
                if (call.args.size != 3) return EvalValue.Error("IF requires 3 arguments")
                when (val cond = arg(0)) {
                    is EvalValue.Error -> cond
                    is EvalValue.Text -> if (cond.value.isNotBlank()) arg(1) else arg(2)
                    is EvalValue.Number -> if (cond.value != 0.0) arg(1) else arg(2)
                }
            }
            "ISNUMBER" -> {
                if (call.args.size != 1) return EvalValue.Error("ISNUMBER requires 1 argument")
                EvalValue.Number(if (arg(0) is EvalValue.Number) 1.0 else 0.0)
            }
            "ISFIRST" -> EvalValue.Number(if (rowIndex == 0) 1.0 else 0.0)
            "INPUT" -> EvalValue.Number(rows[rowIndex].cells[column.key] ?: 0.0)
            "CARRY", "PREV" -> {
                if (call.args.size != 1 || call.args[0] !is Expr.Ident) {
                    return EvalValue.Error("${call.name} requires a column name")
                }
                if (rowIndex == 0) return EvalValue.Number(0.0)
                val name = (call.args[0] as Expr.Ident).name
                val target = template.columns.firstOrNull { it.key == name }
                    ?: return EvalValue.Error("Unknown column '$name'")
                valueOf(template, rows, rowIndex - 1, target, cache, visiting).toValue()
            }
            "WEEKDAY" -> {
                val date = rows[rowIndex].date
                val text = LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                EvalValue.Text(text)
            }
            else -> EvalValue.Error("Unknown function ${call.name}")
        }
    }

    private fun ComputedCell.toValue(): EvalValue {
        return when {
            error != null -> EvalValue.Error(error)
            number != null -> EvalValue.Number(number)
            text != null -> EvalValue.Text(text)
            else -> EvalValue.Number(0.0)
        }
    }

    private fun EvalValue.numberOrError(): EvalValue = when (this) {
        is EvalValue.Number, is EvalValue.Error -> this
        is EvalValue.Text -> EvalValue.Error("Expected a number")
    }

    private fun EvalValue.mapNumber(transform: (Double) -> Double): EvalValue = when (this) {
        is EvalValue.Number -> EvalValue.Number(transform(value))
        else -> this
    }

    private sealed class EvalValue {
        data class Number(val value: Double) : EvalValue()
        data class Text(val value: String) : EvalValue()
        data class Error(val message: String) : EvalValue()
    }
}
