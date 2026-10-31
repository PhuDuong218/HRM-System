# Decisions and open questions

## Confirmed in this chat
- Check-in early allowance: 30 minutes; late threshold: 15 minutes. This overrides the prompt wording ±15 minutes. Confirmed follow-up: after +15 minutes continue recording actual check-in time and flag lateness; do not reject solely for lateness.
- Remote hrm_test is isolated; writes, concurrency and jobs are authorized. Never write to hrm_db.
- Preserve current Duong working tree. Self-service mobile is API-only in this repository.
- Payroll tax tiers above tier 1 remain unconfirmed; do not redefine tax or add dependants/holiday rates/insurance limits.
- Xuân Anh personnel scope is established in previous conversation. The user subsequently approved fixing the demonstrated payroll authorization and meal-exemption defects in team modules; those three production files and two regression tests have been changed. Other findings remain documented separately.

## Still open; do not infer answers
- Full team assignment document for mapping six placeholder screens to owners and acceptance obligations.
- Approved payroll tax thresholds/rates above tier 1.
- Required scale/performance SLA and multi-instance deployment acceptance.
- Whether historically exposed SQL credentials have been rotated. Do not export their values.
- ZIP equivalence has not been verified; current working tree is the test target.
