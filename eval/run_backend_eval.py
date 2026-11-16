#!/usr/bin/env python3
"""Backend evaluation: product identification + retrieval. Standard library only.

Start the backend first, then:
    python eval/run_backend_eval.py --base http://localhost:8080 \
        --identify eval/cases_identify.json --retrieval eval/cases_retrieval.json

Identification metrics (per case: expected variant id, or null = not in catalogue):
  - accuracy: confident AND correct, or (expected null AND clarification/no candidates)
  - confident_wrong: returned a confident match that is wrong  ← main safety metric, lower is better
  - top1_correct: right product ranked first (even if the app asked to confirm)
  - field-level exact match (photo cases with "expectedFields" only): per field, after the same
    normalisation the matcher uses; a null where text was printed counts as wrong
    (Lecture 6, slide 19: field-level exact match against a truth file; count the nulls)
Retrieval metrics: hit@1 and hit@3 = the expected section type is in the top-k passages,
  next to the chance level of a random ranking, k / number of sections (Lecture 5, slide 32, decision 5).

Every rate is reported with a 95% Wilson score interval, overall and per language.
With n of 10-50 the intervals are wide: differences inside them are not evidence (Lecture 4, §10-12).
Reference: Wilson, E. B. (1927), "Probable inference, the law of succession, and statistical inference".
"""
import argparse
import csv
import json
import math
import mimetypes
import pathlib
import re
import time
import urllib.request
import uuid


def wilson(k, n, z=1.96):
    if n == 0:
        return (float("nan"), float("nan"))
    p = k / n
    denom = 1 + z * z / n
    centre = (p + z * z / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    return (max(0.0, centre - half), min(1.0, centre + half))


def post_json(url, payload):
    req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"),
                                 headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def post_image(url, path):
    boundary = uuid.uuid4().hex
    data = pathlib.Path(path).read_bytes()
    ctype = mimetypes.guess_type(path)[0] or "image/jpeg"
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"{pathlib.Path(path).name}\"\r\n"
            f"Content-Type: {ctype}\r\n\r\n").encode() + data + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(url, data=body, method="POST",
                                 headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def fmt(name, k, n):
    lo, hi = wilson(k, n)
    return f"{name:<18} {k:>3}/{n:<3} = {k / n if n else float('nan'):.2f}   95% CI [{lo:.2f}, {hi:.2f}]"


def norm_field(name, v):
    """Same normalisation idea as the backend matcher (TextNorm), simplified for comparison."""
    if v is None:
        return None
    t = str(v).lower().replace("ё", "е")
    if name == "strength":
        t = t.replace(",", ".").replace(" ", "").replace("mg", "мг").replace("ml", "мл")
    return " ".join(re.findall(r"[\w./]+", t))


def by_lang(rows_subset, key):
    out = {}
    for r in rows_subset:
        out.setdefault(r["lang"], []).append(r[key])
    return out


def report(title, results, metrics):
    print(f"\n== {title} ==")
    for m in metrics:
        vals = [r[m] for r in results]
        print(fmt(m, sum(vals), len(vals)))
        langs = by_lang(results, m)
        if len(langs) > 1:
            for lang, v in sorted(langs.items()):
                print("   " + fmt(f"{m} [{lang}]", sum(v), len(v)))


def eval_identify(base, cases, rows):
    results, field_hits = [], {}
    for c in cases:
        t0 = time.time()
        if c.get("image"):
            r = post_image(f"{base}/api/identify", c["image"])
        else:
            r = post_json(f"{base}/api/identify/fields", c["fields"])
        ms = int((time.time() - t0) * 1000)
        cands = r.get("candidates", [])
        got = cands[0]["variantId"] if cands else None
        confident = not r.get("needsClarification", True)
        exp = c.get("expected")
        if exp is None:
            ok = not confident
            wrong = confident
        else:
            ok = confident and got == exp
            wrong = confident and got != exp
        t1 = (got == exp) if exp is not None else (got is None or not confident)
        results.append({"lang": c.get("lang", "ru"), "accuracy": ok, "confident_wrong": wrong, "top1_correct": t1})
        if c.get("expectedFields") and r.get("extracted") is not None:
            for f, truth in c["expectedFields"].items():
                hit = norm_field(f, r["extracted"].get(f)) == norm_field(f, truth)
                field_hits.setdefault(f, []).append(hit)
        rows.append(["identify", c["id"], c.get("lang", "ru"), exp, got, confident, ok, wrong,
                     ";".join(r.get("reasons", [])), ms])
    report("Identification", results, ["accuracy", "confident_wrong", "top1_correct"])
    if field_hits:
        print("\n== Field-level exact match (photo cases) ==")
        for f, hits in field_hits.items():
            print(fmt(f, sum(hits), len(hits)))


def eval_retrieval(base, cases, rows):
    results, chance1, chance3 = [], [], []
    section_counts = {}
    for c in cases:
        vid = c["variantId"]
        if vid not in section_counts:
            with urllib.request.urlopen(f"{base}/api/products/{vid}/leaflet?lang={c.get('lang', 'ru')}", timeout=30) as resp:
                section_counts[vid] = len(json.loads(resp.read().decode("utf-8")).get("sections", [])) or 1
        n_sec = section_counts[vid]
        chance1.append(min(1.0, 1 / n_sec))
        chance3.append(min(1.0, 3 / n_sec))
        t0 = time.time()
        r = post_json(f"{base}/api/products/{c['variantId']}/retrieve",
                      {"question": c["question"], "lang": c.get("lang", "ru"), "k": 3})
        ms = int((time.time() - t0) * 1000)
        types = [p["sectionType"] for p in r.get("passages", [])]
        hit1 = bool(types) and types[0] == c["expectedSection"]
        hit3 = c["expectedSection"] in types[:3]
        results.append({"lang": c.get("lang", "ru"), "hit@1": hit1, "hit@3": hit3})
        rows.append(["retrieval", c["id"], c.get("lang", "ru"), c["expectedSection"], "|".join(types),
                     "", hit1, hit3, "", ms])
    report("Retrieval", results, ["hit@1", "hit@3"])
    if results:
        print(f"   chance level (random ranking): hit@1 {sum(chance1) / len(chance1):.2f}, "
              f"hit@3 {sum(chance3) / len(chance3):.2f}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--identify", default="eval/cases_identify.json")
    ap.add_argument("--retrieval", default="eval/cases_retrieval.json")
    ap.add_argument("--out", default="eval/results_backend.csv")
    ap.add_argument("--split", choices=["all", "tune", "heldout"], default="all",
                    help="run only cases with this split tag (cases without a tag count as 'tune')")
    a = ap.parse_args()

    def load(p):
        cases = json.loads(pathlib.Path(p).read_text(encoding="utf-8"))
        return cases if a.split == "all" else [c for c in cases if c.get("split", "tune") == a.split]

    rows = []
    eval_identify(a.base, load(a.identify), rows)
    eval_retrieval(a.base, load(a.retrieval), rows)
    with open(a.out, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["task", "case", "lang", "expected", "got", "confident", "correct_or_hit1", "wrong_or_hit3", "reasons", "ms"])
        w.writerows(rows)
    print(f"\nPer-case results: {a.out}")


if __name__ == "__main__":
    main()
