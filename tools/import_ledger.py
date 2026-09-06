#!/usr/bin/env python3
"""Convert Ledger.xlsx into bundled seed JSON for the Android app."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import sys
from pathlib import Path

try:
    import openpyxl
except ImportError as exc:  # pragma: no cover
    raise SystemExit("openpyxl is required. Install it with: pip install openpyxl") from exc


WEEKDAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]


def iso(value) -> str | None:
    if isinstance(value, dt.datetime):
        return value.date().isoformat()
    if isinstance(value, dt.date):
        return value.isoformat()
    return None


def num(value) -> float | None:
    if value is None or value == "":
        return None
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def commodity_columns(prefix: str, group: str, rate_key: str, divide: bool = True) -> list[dict]:
    spend = f"{prefix}_expenditure"
    opening = f"{prefix}_opening"
    supply = f"{prefix}_supply"
    total = f"{prefix}_total"
    balance = f"{prefix}_balance"
    rate_expr = f"count * {rate_key}" + (" / 1000" if divide else "")
    return [
        {
            "key": opening,
            "label": "Opening",
            "groupLabel": group,
            "valueType": "NUMBER",
            "unit": "kg" if divide else "INR",
            "role": "OPENING",
            "formula": f"IF(ISFIRST(), INPUT(), CARRY({balance}))",
            "warnNegative": False,
        },
        {
            "key": supply,
            "label": "Supply",
            "groupLabel": group,
            "valueType": "NUMBER",
            "unit": "kg" if divide else "INR",
            "role": "INPUT",
            "formula": None,
            "warnNegative": False,
        },
        {
            "key": total,
            "label": "Total",
            "groupLabel": group,
            "valueType": "NUMBER",
            "unit": "kg" if divide else "INR",
            "role": "COMPUTED",
            "formula": f"SUM({opening}, {supply})",
            "warnNegative": False,
        },
        {
            "key": spend,
            "label": "Expenditure",
            "groupLabel": group,
            "valueType": "NUMBER",
            "unit": "kg" if divide else "INR",
            "role": "COMPUTED",
            "formula": rate_expr,
            "warnNegative": False,
        },
        {
            "key": balance,
            "label": "Balance",
            "groupLabel": group,
            "valueType": "NUMBER",
            "unit": "kg" if divide else "INR",
            "role": "COMPUTED",
            "formula": f"{total} - {spend}",
            "warnNegative": True,
        },
    ]


def base_date_columns() -> list[dict]:
    return [
        {
            "key": "date",
            "label": "Date",
            "groupLabel": None,
            "valueType": "DATE",
            "unit": None,
            "role": "DATE",
            "formula": None,
            "warnNegative": False,
        },
        {
            "key": "week",
            "label": "Week",
            "groupLabel": None,
            "valueType": "WEEKDAY",
            "unit": None,
            "role": "COMPUTED",
            "formula": "WEEKDAY(date)",
            "warnNegative": False,
        },
    ]


def count_column(key: str = "count", label: str = "Count") -> dict:
    return {
        "key": key,
        "label": label,
        "groupLabel": None,
        "valueType": "NUMBER",
        "unit": "children",
        "role": "INPUT",
        "formula": None,
        "warnNegative": False,
    }


def templates() -> list[dict]:
    rice_8 = {
        "id": "rice_8",
        "name": "8th Rice Bhagya",
        "description": "Daily rice, dal, oil, and wheat for Class 8",
        "sheetName": "8th Rice Bagya",
        "gradeIds": ["grade_8"],
        "rates": [
            {"key": "rice_rate", "label": "Rice per child", "value": 150.0, "unit": "g"},
            {"key": "dal_rate", "label": "Dal per child", "value": 30.0, "unit": "g"},
            {"key": "oil_rate", "label": "Oil per child", "value": 6.5, "unit": "g"},
            {"key": "wheat_rate", "label": "Wheat per child", "value": 150.0, "unit": "g"},
        ],
        "columns": [
            *base_date_columns(),
            count_column(),
            *commodity_columns("rice", "Rice", "rice_rate"),
            *commodity_columns("dal", "Dal", "dal_rate"),
            *commodity_columns("oil", "Oil", "oil_rate"),
            *commodity_columns("wheat", "Wheat", "wheat_rate"),
        ],
    }
    rice_9_10 = {
        "id": "rice_9_10",
        "name": "9th & 10th Rice Bhagya",
        "description": "Combined rice, dal, and oil for Classes 9 and 10",
        "sheetName": "9th & 10 Rice Bagya",
        "gradeIds": ["grade_9", "grade_10"],
        "rates": [
            {"key": "rice_rate", "label": "Rice per child", "value": 150.0, "unit": "g"},
            {"key": "dal_rate", "label": "Dal per child", "value": 30.0, "unit": "g"},
            {"key": "oil_rate", "label": "Oil per child", "value": 6.5, "unit": "g"},
        ],
        "columns": [
            *base_date_columns(),
            count_column(),
            *commodity_columns("rice", "Rice", "rice_rate"),
            *commodity_columns("dal", "Dal", "dal_rate"),
            *commodity_columns("oil", "Oil", "oil_rate"),
        ],
    }
    milk = {
        "id": "milk_biscuit",
        "name": "Milk & Biscuit",
        "description": "Milk and malt powder for all classes",
        "sheetName": "Milk & Biscut",
        "gradeIds": ["grade_8", "grade_9", "grade_10"],
        "rates": [
            {"key": "milk_rate", "label": "Milk per child", "value": 18.0, "unit": "g"},
            {"key": "malt_rate", "label": "Malt powder per child", "value": 5.0, "unit": "g"},
        ],
        "columns": [
            *base_date_columns(),
            count_column(),
            *commodity_columns("milk", "Milk", "milk_rate"),
            *commodity_columns("malt", "Malt Powder", "malt_rate"),
        ],
    }
    contingency = {
        "id": "contingency",
        "name": "Contingency",
        "description": "Grade contingency and sugar contingency in rupees",
        "sheetName": "Contengency",
        "gradeIds": ["grade_8", "grade_9", "grade_10"],
        "rates": [
            {"key": "contingency_rate", "label": "Contingency per child", "value": 3.55, "unit": "INR"},
            {"key": "sugar_rate", "label": "Sugar contingency per child", "value": 0.44, "unit": "INR"},
        ],
        "columns": [
            *base_date_columns(),
            count_column("count_8", "8th Head Count"),
            *commodity_columns("cont_8", "8th Contingency", "contingency_rate", divide=False),
            count_column("count_9", "9th Head Count"),
            count_column("count_10", "10th Head Count"),
            {
                "key": "count_9_10",
                "label": "9th + 10th Total",
                "groupLabel": None,
                "valueType": "NUMBER",
                "unit": "children",
                "role": "COMPUTED",
                "formula": "SUM(count_9, count_10)",
                "warnNegative": False,
            },
            *commodity_columns("cont_9_10", "9th & 10th Contingency", "contingency_rate", divide=False),
            {
                "key": "count_all",
                "label": "8, 9, 10 Total",
                "groupLabel": None,
                "valueType": "NUMBER",
                "unit": "children",
                "role": "COMPUTED",
                "formula": "SUM(count_8, count_9_10)",
                "warnNegative": False,
            },
            *commodity_columns("sugar", "Sugar Contingency", "sugar_rate", divide=False),
        ],
    }
    # Fix contingency spend formulas to use the matching count columns.
    for col in contingency["columns"]:
        if col["key"] == "cont_8_expenditure":
            col["formula"] = "count_8 * contingency_rate"
        elif col["key"] == "cont_9_10_expenditure":
            col["formula"] = "count_9_10 * contingency_rate"
        elif col["key"] == "sugar_expenditure":
            col["formula"] = "count_all * sugar_rate"

    egg = {
        "id": "egg_banana",
        "name": "Egg, Banana & Nut Bar",
        "description": "Daily egg and banana counts with cash contingency",
        "sheetName": "Egg & Bannana & nutbar",
        "gradeIds": ["grade_8", "grade_9", "grade_10"],
        "rates": [
            {"key": "egg_rate", "label": "Egg contingency", "value": 6.0, "unit": "INR"},
            {"key": "banana_rate", "label": "Banana contingency", "value": 5.7, "unit": "INR"},
        ],
        "columns": [
            *base_date_columns(),
            {
                "key": "egg_8",
                "label": "Egg",
                "groupLabel": "8th",
                "valueType": "NUMBER",
                "unit": "count",
                "role": "INPUT",
                "formula": None,
                "warnNegative": False,
            },
            {
                "key": "banana_8",
                "label": "Banana",
                "groupLabel": "8th",
                "valueType": "NUMBER",
                "unit": "count",
                "role": "INPUT",
                "formula": None,
                "warnNegative": False,
            },
            {
                "key": "total_8",
                "label": "Total",
                "groupLabel": "8th",
                "valueType": "NUMBER",
                "unit": "count",
                "role": "COMPUTED",
                "formula": "SUM(egg_8, banana_8)",
                "warnNegative": False,
            },
            {
                "key": "egg_9",
                "label": "Egg",
                "groupLabel": "9th",
                "valueType": "NUMBER",
                "unit": "count",
                "role": "INPUT",
                "formula": None,
                "warnNegative": False,
            },
            {
                "key": "banana_9",
                "label": "Banana",
                "groupLabel": "9th",
                "valueType": "NUMBER",
                "unit": "count",
                "role": "INPUT",
                "formula": None,
                "warnNegative": False,
            },
            {
                "key": "total_9",
                "label": "Total",
                "groupLabel": "9th",
                "valueType": "NUMBER",
                "unit": "count",
                "role": "COMPUTED",
                "formula": "SUM(egg_9, banana_9)",
                "warnNegative": False,
            },
            {
                "key": "egg_10",
                "label": "Egg",
                "groupLabel": "10th",
                "valueType": "NUMBER",
                "unit": "count",
                "role": "INPUT",
                "formula": None,
                "warnNegative": False,
            },
            {
                "key": "banana_10",
                "label": "Banana",
                "groupLabel": "10th",
                "valueType": "NUMBER",
                "unit": "count",
                "role": "INPUT",
                "formula": None,
                "warnNegative": False,
            },
            {
                "key": "total_10",
                "label": "Total",
                "groupLabel": "10th",
                "valueType": "NUMBER",
                "unit": "count",
                "role": "COMPUTED",
                "formula": "SUM(egg_10, banana_10)",
                "warnNegative": False,
            },
            {
                "key": "egg_total",
                "label": "Egg",
                "groupLabel": "Grand Total",
                "valueType": "NUMBER",
                "unit": "count",
                "role": "COMPUTED",
                "formula": "SUM(egg_8, egg_9, egg_10)",
                "warnNegative": False,
            },
            {
                "key": "banana_total",
                "label": "Banana",
                "groupLabel": "Grand Total",
                "valueType": "NUMBER",
                "unit": "count",
                "role": "COMPUTED",
                "formula": "SUM(banana_8, banana_9, banana_10)",
                "warnNegative": False,
            },
            {
                "key": "combo_total",
                "label": "Total",
                "groupLabel": "Grand Total",
                "valueType": "NUMBER",
                "unit": "count",
                "role": "COMPUTED",
                "formula": "SUM(egg_total, banana_total)",
                "warnNegative": False,
            },
            {
                "key": "egg_cont",
                "label": "Egg Contingency",
                "groupLabel": "Cash",
                "valueType": "NUMBER",
                "unit": "INR",
                "role": "COMPUTED",
                "formula": "egg_total * egg_rate",
                "warnNegative": False,
            },
            {
                "key": "banana_cont",
                "label": "Banana Contingency",
                "groupLabel": "Cash",
                "valueType": "NUMBER",
                "unit": "INR",
                "role": "COMPUTED",
                "formula": "banana_total * banana_rate",
                "warnNegative": False,
            },
            {
                "key": "cash_opening",
                "label": "Opening",
                "groupLabel": "Egg/Banana Fund",
                "valueType": "NUMBER",
                "unit": "INR",
                "role": "OPENING",
                "formula": "IF(ISFIRST(), INPUT(), CARRY(cash_balance))",
                "warnNegative": False,
            },
            {
                "key": "cash_supply",
                "label": "Supply",
                "groupLabel": "Egg/Banana Fund",
                "valueType": "NUMBER",
                "unit": "INR",
                "role": "INPUT",
                "formula": None,
                "warnNegative": False,
            },
            {
                "key": "cash_total",
                "label": "Total",
                "groupLabel": "Egg/Banana Fund",
                "valueType": "NUMBER",
                "unit": "INR",
                "role": "COMPUTED",
                "formula": "SUM(cash_opening, cash_supply)",
                "warnNegative": False,
            },
            {
                "key": "cash_expenditure",
                "label": "Expenditure",
                "groupLabel": "Egg/Banana Fund",
                "valueType": "NUMBER",
                "unit": "INR",
                "role": "COMPUTED",
                "formula": "SUM(egg_cont, banana_cont)",
                "warnNegative": False,
            },
            {
                "key": "cash_balance",
                "label": "Balance",
                "groupLabel": "Egg/Banana Fund",
                "valueType": "NUMBER",
                "unit": "INR",
                "role": "COMPUTED",
                "formula": "cash_total - cash_expenditure",
                "warnNegative": True,
            },
        ],
    }
    return [rice_8, rice_9_10, milk, contingency, egg]


def assign_column_ids(items: list[dict]) -> None:
    for template in items:
        for index, column in enumerate(template["columns"]):
            column["id"] = f"{template['id']}_{column['key']}"
            column["sortOrder"] = index
            column["visible"] = True
        for index, rate in enumerate(template["rates"]):
            rate["id"] = f"{template['id']}_{rate['key']}"
            rate["sortOrder"] = index


def rows_from_sheet(ws, mapper) -> list[dict]:
    rows = []
    for r in range(3, ws.max_row + 1):
        date_value = iso(ws.cell(r, 1).value)
        if not date_value:
            continue
        cells, source = mapper(ws, r)
        parsed = dt.date.fromisoformat(date_value)
        rows.append(
            {
                "date": date_value,
                "sortOrder": parsed.day,
                "cells": {k: v for k, v in cells.items() if v is not None},
                "sourceValues": {k: v for k, v in source.items() if v is not None},
            }
        )
    return rows


def commodity_source(ws, r, prefix: str, start_col: int) -> dict:
    keys = [f"{prefix}_opening", f"{prefix}_supply", f"{prefix}_total", f"{prefix}_expenditure", f"{prefix}_balance"]
    return {key: num(ws.cell(r, start_col + i).value) for i, key in enumerate(keys)}


def commodity_inputs(ws, r, prefix: str, start_col: int, first: bool) -> dict:
    opening = num(ws.cell(r, start_col).value)
    supply = num(ws.cell(r, start_col + 1).value)
    cells = {f"{prefix}_supply": supply if supply is not None else 0.0}
    if first:
        cells[f"{prefix}_opening"] = opening if opening is not None else 0.0
    return cells


def map_rice_8(ws, r):
    first = r == 3
    cells = {"count": num(ws.cell(r, 3).value)}
    source = {"count": cells["count"]}
    for prefix, col in (("rice", 4), ("dal", 9), ("oil", 14), ("wheat", 19)):
        cells.update(commodity_inputs(ws, r, prefix, col, first))
        source.update(commodity_source(ws, r, prefix, col))
    return cells, source


def map_rice_9_10(ws, r):
    first = r == 3
    cells = {"count": num(ws.cell(r, 3).value)}
    source = {"count": cells["count"]}
    for prefix, col in (("rice", 4), ("dal", 9), ("oil", 14)):
        cells.update(commodity_inputs(ws, r, prefix, col, first))
        source.update(commodity_source(ws, r, prefix, col))
    return cells, source


def map_milk(ws, r):
    first = r == 3
    cells = {"count": num(ws.cell(r, 3).value)}
    source = {"count": cells["count"]}
    for prefix, col in (("milk", 4), ("malt", 9)):
        cells.update(commodity_inputs(ws, r, prefix, col, first))
        source.update(commodity_source(ws, r, prefix, col))
    return cells, source


def map_contingency(ws, r):
    first = r == 3
    cells = {
        "count_8": num(ws.cell(r, 3).value),
        "count_9": num(ws.cell(r, 9).value),
        "count_10": num(ws.cell(r, 10).value),
    }
    source = dict(cells)
    source["count_9_10"] = num(ws.cell(r, 11).value)
    source["count_all"] = num(ws.cell(r, 17).value)
    cells.update(commodity_inputs(ws, r, "cont_8", 4, first))
    cells.update(commodity_inputs(ws, r, "cont_9_10", 12, first))
    cells.update(commodity_inputs(ws, r, "sugar", 18, first))
    source.update(commodity_source(ws, r, "cont_8", 4))
    source.update(commodity_source(ws, r, "cont_9_10", 12))
    source.update(commodity_source(ws, r, "sugar", 18))
    return cells, source


def map_egg(ws, r):
    first = r == 3
    cells = {
        "egg_8": num(ws.cell(r, 3).value),
        "banana_8": num(ws.cell(r, 4).value),
        "egg_9": num(ws.cell(r, 6).value),
        "banana_9": num(ws.cell(r, 7).value),
        "egg_10": num(ws.cell(r, 9).value),
        "banana_10": num(ws.cell(r, 10).value),
        "cash_supply": num(ws.cell(r, 18).value) or 0.0,
    }
    if first:
        cells["cash_opening"] = num(ws.cell(r, 17).value) or 0.0
    source = {
        **{k: cells.get(k) for k in ("egg_8", "banana_8", "egg_9", "banana_9", "egg_10", "banana_10")},
        "total_8": num(ws.cell(r, 5).value),
        "total_9": num(ws.cell(r, 8).value),
        "total_10": num(ws.cell(r, 11).value),
        "egg_total": num(ws.cell(r, 12).value),
        "banana_total": num(ws.cell(r, 13).value),
        "combo_total": num(ws.cell(r, 14).value),
        "egg_cont": num(ws.cell(r, 15).value),
        "banana_cont": num(ws.cell(r, 16).value),
        "cash_opening": num(ws.cell(r, 17).value),
        "cash_supply": num(ws.cell(r, 18).value),
        "cash_total": num(ws.cell(r, 19).value),
        "cash_expenditure": num(ws.cell(r, 20).value),
        "cash_balance": num(ws.cell(r, 21).value),
    }
    return cells, source


MAPPERS = {
    "8th Rice Bagya": ("rice_8", map_rice_8),
    "9th & 10 Rice Bagya": ("rice_9_10", map_rice_9_10),
    "Milk & Biscut": ("milk_biscuit", map_milk),
    "Contengency": ("contingency", map_contingency),
    "Egg & Bannana & nutbar": ("egg_banana", map_egg),
}


def convert(workbook_path: Path) -> dict:
    wb = openpyxl.load_workbook(workbook_path, data_only=True)
    items = templates()
    assign_column_ids(items)
    by_id = {item["id"]: item for item in items}
    instances = []
    for sheet_name, (template_id, mapper) in MAPPERS.items():
        ws = wb[sheet_name]
        rows = rows_from_sheet(ws, mapper)
        if not rows:
            continue
        first = dt.date.fromisoformat(rows[0]["date"])
        instances.append(
            {
                "id": f"{template_id}_{first.year}_{first.month:02d}",
                "templateId": template_id,
                "year": first.year,
                "month": first.month,
                "title": f"{by_id[template_id]['name']} {first.strftime('%B %Y')}",
                "rows": rows,
            }
        )
    return {
        "version": 1,
        "school": {
            "name": "Akshara Dasoha",
            "program": "Mid-Day Meal Ledger",
        },
        "grades": [
            {"id": "grade_8", "name": "8th", "sortOrder": 0},
            {"id": "grade_9", "name": "9th", "sortOrder": 1},
            {"id": "grade_10", "name": "10th", "sortOrder": 2},
        ],
        "templates": items,
        "instances": instances,
        "corrections": [
            {
                "item": "oil_rate_9_10",
                "workbook": 76.5,
                "app": 6.5,
                "reason": "Sheet header is Oil (6.500). The app uses the header rate instead of the 76.5 workbook formula.",
            },
            {
                "item": "banana_rate",
                "workbook": 6.0,
                "app": 5.7,
                "reason": "Sheet header is banana cont (5.7). The app uses 5.7 instead of the workbook *6 formula and the broken P4 cell.",
            },
            {
                "item": "first_day_rice_dal_expenditure",
                "workbook": 0.0,
                "app": 1.0,
                "reason": "The first rice and dal expenditure cells on 8th Rice Bhagya were blank in Excel. The app calculates them from the head count and header rates.",
            },
        ],
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workbook", type=Path, default=Path("Ledger.xlsx"))
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/seed/ledger.json"))
    args = parser.parse_args(argv)
    if not args.workbook.exists():
        raise SystemExit(f"Workbook not found: {args.workbook}")
    payload = convert(args.workbook)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {args.output} with {len(payload['instances'])} instances")
    return 0


if __name__ == "__main__":
    sys.exit(main())
