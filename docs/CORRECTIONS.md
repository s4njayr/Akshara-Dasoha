# Workbook corrections

The Android app imports every historical row from `Ledger.xlsx` and keeps the original sheet values on each imported day for audit. Calculated columns then use editable header rates.

| Item | Workbook | App | Why |
| --- | --- | --- | --- |
| 9th & 10th oil rate | `Count * 76.5 / 1000` | `Count * 6.5 / 1000` | The sheet header is Oil (6.500). The 76.5 formula did not match the header. |
| Banana contingency | `banana total * 6`, and P4 pointed at a missing cell | `banana_total * 5.7` | The header is banana cont (5.7). |

Rice, dal, wheat, milk, malt, grade contingency, sugar, and egg rates match the workbook headers. The first 8th-grade rice and dal expenditure cells were blank in Excel, and later wheat expenditure cells were left uncached at 0; the app calculates all of them from the head count.

Because oil and banana rates are corrected, 9th/10th oil balances and egg/banana cash balances will differ from the Excel file after the first spending day. Change any rate later from **Configure ledgers**.
