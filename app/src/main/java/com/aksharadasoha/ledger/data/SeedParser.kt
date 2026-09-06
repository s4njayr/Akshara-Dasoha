package com.aksharadasoha.ledger.data

import com.aksharadasoha.ledger.domain.AppSettings
import com.aksharadasoha.ledger.domain.ColorPalette
import com.aksharadasoha.ledger.domain.ColumnDef
import com.aksharadasoha.ledger.domain.ColumnRole
import com.aksharadasoha.ledger.domain.CorrectionNote
import com.aksharadasoha.ledger.domain.ExportFormat
import com.aksharadasoha.ledger.domain.Grade
import com.aksharadasoha.ledger.domain.LedgerInstance
import com.aksharadasoha.ledger.domain.LedgerRow
import com.aksharadasoha.ledger.domain.LedgerTemplate
import com.aksharadasoha.ledger.domain.LedgerZoom
import com.aksharadasoha.ledger.domain.RateDef
import com.aksharadasoha.ledger.domain.SchoolProfile
import com.aksharadasoha.ledger.domain.SeedPayload
import com.aksharadasoha.ledger.domain.TableDensity
import com.aksharadasoha.ledger.domain.ThemeMode
import com.aksharadasoha.ledger.domain.ValueType
import org.json.JSONArray
import org.json.JSONObject

object SeedParser {
    fun parse(json: String): SeedPayload {
        val root = JSONObject(json)
        return SeedPayload(
            version = root.optInt("version", 1),
            school = root.optJSONObject("school")?.let {
                SchoolProfile(it.optString("name"), it.optString("program"))
            } ?: SchoolProfile("Akshara Dasoha", "Mid-Day Meal Ledger"),
            grades = root.optJSONArray("grades").orEmpty().map { item ->
                Grade(item.getString("id"), item.getString("name"), item.optInt("sortOrder"))
            },
            templates = root.optJSONArray("templates").orEmpty().map { parseTemplate(it) },
            instances = root.optJSONArray("instances").orEmpty().map { parseInstance(it) },
            corrections = root.optJSONArray("corrections").orEmpty().map { item ->
                CorrectionNote(
                    item.optString("item"),
                    item.optDouble("workbook"),
                    item.optDouble("app"),
                    item.optString("reason"),
                )
            },
        )
    }

    fun toJson(payload: SeedPayload): String {
        val root = JSONObject()
        root.put("version", payload.version)
        root.put("school", JSONObject().put("name", payload.school.name).put("program", payload.school.program))
        root.put("grades", JSONArray().also { array ->
            payload.grades.forEach { grade ->
                array.put(JSONObject().put("id", grade.id).put("name", grade.name).put("sortOrder", grade.sortOrder))
            }
        })
        root.put("templates", JSONArray().also { array ->
            payload.templates.forEach { array.put(templateJson(it)) }
        })
        root.put("instances", JSONArray().also { array ->
            payload.instances.forEach { array.put(instanceJson(it)) }
        })
        root.put("corrections", JSONArray().also { array ->
            payload.corrections.forEach { note ->
                array.put(
                    JSONObject()
                        .put("item", note.item)
                        .put("workbook", note.workbook)
                        .put("app", note.app)
                        .put("reason", note.reason),
                )
            }
        })
        return root.toString(2)
    }

    fun parseSettings(json: JSONObject?): AppSettings? {
        val item = json ?: return null
        val school = item.optJSONObject("school")?.let {
            SchoolProfile(it.optString("name"), it.optString("program"))
        } ?: return null
        val zoomRaw = item.optDouble("ledgerZoom", LedgerZoom.DEFAULT.toDouble()).toFloat()
        return AppSettings(
            school = school,
            themeMode = ThemeMode.entries.firstOrNull { it.name == item.optString("themeMode") } ?: ThemeMode.SYSTEM,
            tableDensity = TableDensity.entries.firstOrNull { it.name == item.optString("tableDensity") } ?: TableDensity.COMPACT,
            language = item.optString("language", "en"),
            defaultExport = ExportFormat.entries.firstOrNull { it.name == item.optString("defaultExport") } ?: ExportFormat.PDF,
            colorPalette = ColorPalette.entries.firstOrNull { it.name == item.optString("colorPalette") } ?: ColorPalette.DYNAMIC,
            ledgerZoom = LedgerZoom.clamp(zoomRaw),
        )
    }

    fun settingsJson(settings: AppSettings): JSONObject {
        return JSONObject()
            .put("school", JSONObject().put("name", settings.school.name).put("program", settings.school.program))
            .put("themeMode", settings.themeMode.name)
            .put("tableDensity", settings.tableDensity.name)
            .put("language", settings.language)
            .put("defaultExport", settings.defaultExport.name)
            .put("colorPalette", settings.colorPalette.name)
            .put("ledgerZoom", settings.ledgerZoom.toDouble())
    }

    private fun parseTemplate(item: JSONObject): LedgerTemplate {
        val id = item.getString("id")
        return LedgerTemplate(
            id = id,
            name = item.getString("name"),
            description = item.optString("description"),
            sheetName = item.cleanString("sheetName"),
            gradeIds = item.optJSONArray("gradeIds").stringList(),
            columns = item.optJSONArray("columns").orEmpty().mapIndexed { index, column ->
                ColumnDef(
                    id = column.optString("id").ifBlank { "${id}_${column.getString("key")}" },
                    templateId = id,
                    key = column.getString("key"),
                    label = column.getString("label"),
                    groupLabel = column.cleanString("groupLabel"),
                    valueType = ValueType.valueOf(column.optString("valueType", "NUMBER")),
                    unit = column.cleanString("unit"),
                    role = ColumnRole.valueOf(column.optString("role", "INPUT")),
                    formula = column.cleanString("formula"),
                    sortOrder = column.optInt("sortOrder", index),
                    visible = column.optBoolean("visible", true),
                    warnNegative = column.optBoolean("warnNegative", false),
                )
            },
            rates = item.optJSONArray("rates").orEmpty().mapIndexed { index, rate ->
                RateDef(
                    id = rate.optString("id").ifBlank { "${id}_${rate.getString("key")}" },
                    templateId = id,
                    key = rate.getString("key"),
                    label = rate.getString("label"),
                    value = rate.getDouble("value"),
                    unit = rate.cleanString("unit"),
                    sortOrder = rate.optInt("sortOrder", index),
                )
            },
        )
    }

    private fun parseInstance(item: JSONObject): LedgerInstance {
        val id = item.getString("id")
        return LedgerInstance(
            id = id,
            templateId = item.getString("templateId"),
            year = item.getInt("year"),
            month = item.getInt("month"),
            title = item.optString("title"),
            rows = item.optJSONArray("rows").orEmpty().mapIndexed { index, row ->
                val date = row.getString("date")
                LedgerRow(
                    id = row.optString("id").ifBlank { "${id}_$date" },
                    instanceId = id,
                    date = date,
                    sortOrder = row.optInt("sortOrder", index + 1),
                    cells = row.optJSONObject("cells")?.toDoubleMap().orEmpty(),
                    sourceValues = row.optJSONObject("sourceValues")?.toDoubleMap().orEmpty(),
                    textCells = row.optJSONObject("textCells")?.toStringMap().orEmpty(),
                )
            },
        )
    }

    private fun templateJson(template: LedgerTemplate): JSONObject {
        return JSONObject()
            .put("id", template.id)
            .put("name", template.name)
            .put("description", template.description)
            .put("sheetName", template.sheetName)
            .put("gradeIds", JSONArray(template.gradeIds))
            .put("columns", JSONArray().also { array ->
                template.columns.forEach { column ->
                    array.put(
                        JSONObject()
                            .put("id", column.id)
                            .put("key", column.key)
                            .put("label", column.label)
                            .put("groupLabel", column.groupLabel)
                            .put("valueType", column.valueType.name)
                            .put("unit", column.unit)
                            .put("role", column.role.name)
                            .put("formula", column.formula)
                            .put("sortOrder", column.sortOrder)
                            .put("visible", column.visible)
                            .put("warnNegative", column.warnNegative),
                    )
                }
            })
            .put("rates", JSONArray().also { array ->
                template.rates.forEach { rate ->
                    array.put(
                        JSONObject()
                            .put("id", rate.id)
                            .put("key", rate.key)
                            .put("label", rate.label)
                            .put("value", rate.value)
                            .put("unit", rate.unit)
                            .put("sortOrder", rate.sortOrder),
                    )
                }
            })
    }

    private fun instanceJson(instance: LedgerInstance): JSONObject {
        return JSONObject()
            .put("id", instance.id)
            .put("templateId", instance.templateId)
            .put("year", instance.year)
            .put("month", instance.month)
            .put("title", instance.title)
            .put("rows", JSONArray().also { array ->
                instance.rows.forEach { row ->
                    val cells = JSONObject()
                    row.cells.forEach { (key, value) -> if (value != null) cells.put(key, value) }
                    val source = JSONObject()
                    row.sourceValues.forEach { (key, value) -> if (value != null) source.put(key, value) }
                    val texts = JSONObject()
                    row.textCells.forEach { (key, value) -> if (value.isNotBlank()) texts.put(key, value) }
                    array.put(
                        JSONObject()
                            .put("id", row.id)
                            .put("date", row.date)
                            .put("sortOrder", row.sortOrder)
                            .put("cells", cells)
                            .put("sourceValues", source)
                            .put("textCells", texts),
                    )
                }
            })
    }

    private fun JSONObject.toStringMap(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        keys().forEach { key ->
            if (!isNull(key)) result[key] = optString(key)
        }
        return result
    }

    private fun JSONObject.toDoubleMap(): Map<String, Double?> {
        val result = mutableMapOf<String, Double?>()
        keys().forEach { key ->
            result[key] = if (isNull(key)) null else optDouble(key)
        }
        return result
    }

    private fun JSONArray?.orEmpty(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optJSONObject(index) }
    }

    private fun JSONObject.cleanString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() && it != "null" }
    }

    private fun JSONArray?.stringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
    }
}
