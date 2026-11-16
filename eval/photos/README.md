Photos of our own medicine packages for the evaluation (no people, no personal data).
Name: <variant-id>__<nn>.jpg. Add each as a case in eval/cases_identify.json:
{"id": "ph01", "image": "eval/photos/<file>.jpg", "expected": "<variant-id or null>",
 "expectedFields": {"name": "...", "strength": "...", "form": "...", "manufacturer": "..."},
 "lang": "ru", "split": "heldout"}
Decide the split BEFORE looking at results. About 30% "tune", 70% "heldout".
