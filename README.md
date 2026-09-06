# Akshara Dasoha Ledger

Offline Android app for Karnataka mid-day meal ledgers. It imports `Ledger.xlsx`, organizes books by class, lets you add or remove columns and formulas, prints each month as a landscape PDF, and downloads an editable Excel workbook with native spreadsheet formulas.

**GitHub:** [https://github.com/s4njayr/Akshara-Dasoha](https://github.com/s4njayr/Akshara-Dasoha)

## Clone

```bash
git clone https://github.com/s4njayr/Akshara-Dasoha.git
cd Akshara-Dasoha
```

The repository is private. You need access to `s4njayr/Akshara-Dasoha` before you can clone it.

## Open in Android Studio

1. Install Android Studio and JDK 17.
2. Open the cloned folder as a Gradle project.
3. Let Gradle sync, then run the `app` configuration on a phone or emulator (API 26+).

If the Gradle wrapper jar is missing, generate it once:

```bash
gradle wrapper --gradle-version 8.9
```

## Install on a phone

Use a **signed** APK. Android will not install `app-release-unsigned.apk`.

1. Build from Android Studio (**Build > Generate Signed App Bundle / APK**) or install the debug build onto a device with USB debugging.
2. On the phone, allow install from unknown sources if you are sideloading.
3. If an older build is already installed and signing does not match, uninstall it first.

## Refresh seed data

```bash
python3 -m venv .venv
.venv/bin/pip install openpyxl
.venv/bin/python tools/import_ledger.py
```

## Tests

```bash
.venv/bin/python -m unittest tools/test_import.py
./gradlew test lint
./gradlew connectedAndroidTest   # device or emulator
```

From a month ledger, use **Download PDF** or **Download Excel**. The Excel file opens in Microsoft Excel, LibreOffice, or Google Sheets. Yellow cells are editable inputs; green cells contain native formulas that recalculate on the computer. Rates live on a dedicated sheet.

Workbook rate corrections are listed in [docs/CORRECTIONS.md](docs/CORRECTIONS.md).
