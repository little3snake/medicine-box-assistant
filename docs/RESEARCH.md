# RESEARCH.md — existing solutions and what we learn from them

Desk research done 16 Nov 2026, before implementation, from public app-store pages and websites.
We did not install or test these apps; descriptions are what their developers say about them.

## 1. What already exists

| Solution | What it does (per its own description) | Data source | Gap relevant to us |
|---|---|---|---|
| **register.ndda.kz** (state register, Kazakhstan) | Official register of medicines with leaflets | Official | Search by name only; the user must find the exact variant; long medical text, not phone-friendly |
| **Manufacturer PDFs** (e.g. santo.kz, farmak.kz) | Official leaflet per product as PDF | Official | Scattered by manufacturer; PDF on a phone; no questions |
| **i-teka** (Kazakhstan app) | Finds medicines and prices in 4,000+ pharmacies, 25,000+ products | Pharmacy catalogues | Built for buying and prices, not for understanding the leaflet of the package in hand |
| **MedScan** and similar "AI medicine scanner" apps | Photo of a package → AI identifies it → simple summary, questions | Not stated / "AI analysis" | Answers are AI-generated summaries, not quotes from the official leaflet; country coverage varies; not specific to Kazakhstan |
| **mojApteczka** (Poland) | Scans a package and matches it against the national register (78,000+ products) | Polish national register (RPL) | Closest design to ours (scan → match against an official register); Poland only |
| **Pill identifier apps** (MediLens, PillLens, etc.) | Identify pills by photo, imprint, shape, colour | Mixed public sources | Pills, not packages; often paid; information not tied to a Kazakhstan leaflet |
| **General chatbots / Google Lens** | Answer anything about a photo | Model's training data, web | No guarantee of the exact strength/form/manufacturer; no verbatim official source; may describe another variant |

## 2. What we take from this

1. **Matching against an official register is a proven design** (mojApteczka does it for Poland). We do the
   same for Kazakhstan, with a curated catalogue and the state register as the source.
2. **The common weakness is evidence.** Most scanner apps show AI-written summaries. Our differentiator is
   that every answer line is a **verbatim quote** from the official Kazakhstan leaflet of the **exact
   variant**, or an explicit "not enough information" / "ask a pharmacist".
3. **Look-alike variants are the real risk.** Same trade name, different strength or form → different
   dosing. So we measure *incorrect confident matches* separately and ask the user when unsure.
4. **Language.** None of the apps found offers Kazakh. Our plan includes Russian and Kazakh leaflets,
   with Kazakh checked by a native speaker (Lecture 5 showed retrieval is much weaker for Kazakh
   paraphrases, so this needs its own measurement).
5. **Scope discipline.** We do not try to cover all 25,000+ products in 5 weeks. A curated catalogue of
   40+ variants with an honest evaluation is the goal; coverage is a stretch, not the MVP.

## 3. Official leaflet format (affects our ingestion)

Current Kazakhstan leaflets ("Инструкция по медицинскому применению лекарственного препарата
(Листок-вкладыш)") start with a fixed header — trade name, INN, dosage form and strength,
pharmacotherapeutic group — then fixed sections: indications; "Перечень сведений, необходимых до начала
применения" (contraindications, interactions, special warnings, use in children, pregnancy, driving);
"Рекомендации по применению" (dosing); "Описание нежелательных реакций"; additional information
(composition); storage; shelf life; manufacturer. Older leaflets use headings like "Способ применения и
дозы" and "Побочные действия". `tools/ingest_leaflet.py` recognises both.

## 4. Sources
- i-teka on the App Store: https://apps.apple.com/us/app/id945572681
- MedScan description: https://mwm.ai/apps/medscan/6789895804
- mojApteczka on the App Store: https://apps.apple.com/app/id6760895282
- MediLens on the App Store: https://apps.apple.com/us/app/-/id6749633423
- Pill identifier: MedScan on the App Store: https://apps.apple.com/app/id6747271665
- Example current-format leaflet (Тромбопол, santo.kz): https://santo.kz/wp-content/uploads/2023/02/Проект-1-ИМП-Тромбопол-150мг-1.pdf
- Example current-format leaflet (Бисопролол, santo.kz): https://santo.kz/wp-content/uploads/2023/02/ИМП-Бисопролол.pdf
- Rules for leaflets (old order No. 414, repealed 2020; shows the required structure history): https://adilet.zan.kz/rus/docs/V1500011495
