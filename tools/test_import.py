#!/usr/bin/env python3
"""Verify workbook conversion and formula carry-forward against Ledger.xlsx."""

from __future__ import annotations

import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class ImportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        seed_path = ROOT / "app/src/main/assets/seed/ledger.json"
        if not seed_path.exists():
            from import_ledger import convert

            payload = convert(ROOT / "Ledger.xlsx")
        else:
            payload = json.loads(seed_path.read_text())
        cls.payload = payload

    def test_instance_counts(self) -> None:
        self.assertEqual(len(self.payload["templates"]), 5)
        self.assertEqual(len(self.payload["instances"]), 5)
        self.assertTrue(all(len(item["rows"]) == 31 for item in self.payload["instances"]))

    def test_corrected_rates(self) -> None:
        by_id = {item["id"]: item for item in self.payload["templates"]}
        oil = next(rate for rate in by_id["rice_9_10"]["rates"] if rate["key"] == "oil_rate")
        banana = next(rate for rate in by_id["egg_banana"]["rates"] if rate["key"] == "banana_rate")
        self.assertEqual(oil["value"], 6.5)
        self.assertEqual(banana["value"], 5.7)

    def test_eighth_rice_opening_preserved(self) -> None:
        instance = next(item for item in self.payload["instances"] if item["templateId"] == "rice_8")
        first = instance["rows"][0]["cells"]
        self.assertAlmostEqual(first["rice_opening"], 477.75)
        self.assertAlmostEqual(first["dal_opening"], 44.85)
        last_source = instance["rows"][-1]["sourceValues"]
        self.assertAlmostEqual(last_source["rice_balance"], 460.8, places=2)


if __name__ == "__main__":
    unittest.main()
