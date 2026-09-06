package com.aksharadasoha.ledger.domain

data class ExcelFormulaContext(
    val columnLetters: Map<String, String>,
    val rateRefs: Map<String, String>,
    val row: Int,
    val firstDataRow: Int,
    val currentColumnKey: String,
) {
    fun cell(key: String, atRow: Int = row): String {
        val letter = columnLetters[key] ?: error("Unknown column '$key'")
        return "$letter$atRow"
    }

    fun rate(key: String): String = rateRefs[key] ?: error("Unknown rate '$key'")
}

class ExcelFormulaTranslator {
    fun translate(formula: String, context: ExcelFormulaContext): String {
        val expr = FormulaParser(formula).parse()
        return render(expr, context)
    }

    private fun render(expr: Expr, context: ExcelFormulaContext): String {
        return when (expr) {
            is Expr.NumberLit -> formatNumber(expr.value)
            is Expr.Ident -> {
                when {
                    context.rateRefs.containsKey(expr.name) -> context.rate(expr.name)
                    context.columnLetters.containsKey(expr.name) -> context.cell(expr.name)
                    else -> error("Unknown name '${expr.name}'")
                }
            }
            is Expr.UnaryMinus -> "-${render(expr.inner, context)}"
            is Expr.Binary -> "(${render(expr.left, context)}${expr.op}${render(expr.right, context)})"
            is Expr.Call -> renderCall(expr, context)
        }
    }

    private fun renderCall(call: Expr.Call, context: ExcelFormulaContext): String {
        val args = call.args
        return when (call.name) {
            "SUM" -> args.joinToString("+") { render(it, context) }.let { "($it)" }
            "PRODUCT" -> args.joinToString("*") { render(it, context) }.let { "($it)" }
            "IF" -> {
                require(args.size == 3) { "IF requires 3 arguments" }
                "IF(${render(args[0], context)},${render(args[1], context)},${render(args[2], context)})"
            }
            "ISNUMBER" -> {
                require(args.size == 1) { "ISNUMBER requires 1 argument" }
                "IF(ISNUMBER(${render(args[0], context)}),1,0)"
            }
            "ISFIRST" -> "IF(ROW()=${context.firstDataRow},1,0)"
            "INPUT" -> context.cell(context.currentColumnKey)
            "CARRY", "PREV" -> {
                val name = (args.firstOrNull() as? Expr.Ident)?.name ?: error("${call.name} requires a column name")
                val previous = (context.row - 1).coerceAtLeast(context.firstDataRow)
                "IF(ROW()=${context.firstDataRow},0,${context.cell(name, previous)})"
            }
            "WEEKDAY" -> "TEXT(${context.cell("date")},\"dddd\")"
            else -> error("Unknown function ${call.name}")
        }
    }

    private fun formatNumber(value: Double): String {
        return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    }
}
