#!/usr/bin/env python3
"""Convert an official Kazakhstan leaflet (text copied from register.ndda.kz or the manufacturer's PDF)
into section JSON for the backend.

Usage:
    python tools/ingest_leaflet.py --variant <variant-id> --lang ru \
        --source-url "https://register.ndda.kz/..." --input leaflet.txt

Writes data/leaflets/<variant-id>/<lang>.json and prints a suggested catalogue.json entry
built from the leaflet header (trade name, INN, form and strength) — check it by hand.

How it works: Kazakhstan leaflets ("Инструкция по медицинскому применению (Листок-вкладыш)") use fixed
headings. A short line that STARTS WITH a known heading starts a new section; everything until the next
heading belongs to it. Text is kept VERBATIM (only whitespace at line ends is stripped), because answers
must quote it exactly. If two headings match, the longest wins ("Описание нежелательных реакций" is
side effects, not "Описание"). Unrecognised lines stay in the previous section: read the printed summary.

Headings cover both the current format (with "Перечень сведений, необходимых до начала применения",
"Рекомендации по применению", "Сведения о производителе") and the older one ("Способ применения и дозы",
"Побочные действия"). Kazakh headings: TODO with a native speaker.
"""
import argparse
import datetime
import json
import pathlib
import re
import sys

# type -> heading prefixes (lowercase, ё→е). Keep types in sync with backend SectionTypes.java.
HEADINGS_RU = [
    ("trade_name", ["торговое наименование"]),
    ("inn", ["международное непатентованное название"]),
    ("form", ["лекарственная форма", "описание"]),
    ("pharmacotherapeutic_group", ["фармакотерапевтическая группа"]),
    ("indications", ["показания к применению", "показания"]),
    ("precautions", ["перечень сведений, необходимых до начала применения"]),
    ("contraindications", ["противопоказания"]),
    ("interactions", ["взаимодействия с другими лекарственными препаратами", "лекарственные взаимодействия",
                      "взаимодействие с другими"]),
    ("special_warnings", ["специальные предупреждения", "особые указания", "меры предосторожности"]),
    ("children", ["применение в педиатрии", "особенности применения у детей"]),
    ("pregnancy", ["во время беременности или лактации", "применение в период беременности",
                   "во время беременности", "беременность и лактация"]),
    ("driving", ["особенности влияния лекарственного средства на способность управлять",
                 "особенности влияния препарата на способность управлять",
                 "особенности влияния лекарственного препарата на способность управлять"]),
    ("dosing", ["рекомендации по применению", "режим дозирования", "способ применения и дозы", "способ применения"]),
    ("overdose", ["меры, которые необходимо принять в случае передозировки", "передозировка"]),
    ("side_effects", ["описание нежелательных реакций", "нежелательные реакции", "побочные действия"]),
    ("composition", ["дополнительные сведения", "состав лекарственного препарата", "состав"]),
    ("storage", ["условия хранения"]),
    ("shelf_life", ["срок хранения", "срок годности"]),
    ("dispensing", ["условия отпуска"]),
    ("manufacturer", ["сведения о производителе", "производитель"]),
    ("holder", ["держатель регистрационного удостоверения"]),
    ("contact", ["наименование, адрес и контактные данные"]),
]
MAX_HEADING_LEN = 120


def detect(line: str):
    low = line.strip().lower().replace("ё", "е")
    if not low or len(low) > MAX_HEADING_LEN:
        return None
    best, best_len = None, 0
    for typ, prefixes in HEADINGS_RU:
        for p in prefixes:
            if re.match(re.escape(p) + r"(\b|$)", low) and len(p) > best_len:
                best, best_len = typ, len(p)
    return best


def split_sections(text: str):
    sections, cur = [], None
    for raw in text.splitlines():
        line = raw.rstrip()
        typ = detect(line)
        if typ:
            cur = {"type": typ, "title": line.strip().rstrip(":"), "lines": []}
            sections.append(cur)
        elif cur is not None:
            cur["lines"].append(line)
    out = []
    for s in sections:
        body = "\n".join(s["lines"]).strip()
        if body:
            out.append({"type": s["type"], "title": s["title"], "text": body})
    return out


def first_line(sections, typ):
    for s in sections:
        if s["type"] == typ:
            return s["text"].splitlines()[0].strip()
    return None


def suggest_catalogue_entry(variant, sections):
    """Header fields → a draft catalogue entry. Strength/form are split on the last comma as a guess:
    'Таблетки, покрытые оболочкой, 150 мг' → form 'Таблетки, покрытые оболочкой', strength '150 мг'."""
    form_line = first_line(sections, "form") or ""
    m = re.match(r"^(.*?),\s*([\d.,]+\s*\S+(?:\s*/\s*[\d.,]*\s*\S+)?)\s*$", form_line)
    form, strength = (m.group(1), m.group(2)) if m else (form_line, None)
    return {
        "id": variant,
        "tradeName": (first_line(sections, "trade_name") or "").replace("®", "").strip(),
        "inn": (first_line(sections, "inn") or "").lower(),
        "strength": strength,
        "form": form,
        "manufacturer": first_line(sections, "manufacturer"),
        "registrationNumber": None,
        "lookAlikeGroup": None,
        "sample": False,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--variant", required=True)
    ap.add_argument("--lang", default="ru", choices=["ru", "kk"])
    ap.add_argument("--source-url", required=True)
    ap.add_argument("--input", required=True)
    ap.add_argument("--data-dir", default="data")
    a = ap.parse_args()

    if a.lang != "ru":
        sys.exit("Only Russian headings are supported so far (Kazakh: TODO with a native speaker).")
    text = pathlib.Path(a.input).read_text(encoding="utf-8")
    sections = split_sections(text)
    if not sections:
        sys.exit("No known headings found. Check the input text.")

    out = {
        "variantId": a.variant,
        "lang": a.lang,
        "sourceUrl": a.source_url,
        "retrievedAt": datetime.date.today().isoformat(),
        "sections": sections,
    }
    path = pathlib.Path(a.data_dir) / "leaflets" / a.variant / f"{a.lang}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {path} with {len(sections)} sections:")
    for s in sections:
        print(f"  {s['type']:<26} {s['title'][:55]!r}  ({len(s['text'])} chars)")
    missing = {"indications", "contraindications", "dosing", "side_effects", "storage"} - {s["type"] for s in sections}
    if missing:
        print(f"WARNING: no section found for: {', '.join(sorted(missing))} — check the headings in the text.")
    print("\nSuggested catalogue.json entry (CHECK BY HAND against the package):")
    print(json.dumps(suggest_catalogue_entry(a.variant, sections), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
