package kz.medbox.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Leaflet section types and the words users typically use for them.
 * The keywords are added to each section's indexed text so that a question like
 * "Можно ли детям?" reaches the "children" section even if the official heading is
 * "Особенности применения у детей". This is query/document expansion, a simple
 * alternative to embeddings for the checkpoint.
 *
 * Kazakh keywords are intentionally empty until a native speaker writes them.
 */
public final class SectionTypes {

    private SectionTypes() {}

    /** type → everyday Russian words for that section. Types must match tools/ingest_leaflet.py. */
    public static final Map<String, String> RU_KEYWORDS = new LinkedHashMap<>();

    static {
        RU_KEYWORDS.put("trade_name", "торговое наименование название");
        RU_KEYWORDS.put("inn", "действующее вещество международное непатентованное название");
        RU_KEYWORDS.put("composition", "состав действующее вещество вспомогательные вещества");
        RU_KEYWORDS.put("form", "лекарственная форма дозировка описание внешний вид");
        RU_KEYWORDS.put("pharmacotherapeutic_group", "фармакотерапевтическая группа класс");
        RU_KEYWORDS.put("indications", "показания применению для чего лечение помогает");
        RU_KEYWORDS.put("contraindications", "противопоказания нельзя запрещено кому нельзя");
        RU_KEYWORDS.put("children", "дети ребенок детский возраст детям подростки младенцы педиатрии");
        RU_KEYWORDS.put("pregnancy", "беременность беременным кормление грудью лактация");
        RU_KEYWORDS.put("dosing", "дозы дозировка принимать сколько способ применения режим дозирования");
        RU_KEYWORDS.put("side_effects", "побочные действия побочные эффекты нежелательные реакции");
        RU_KEYWORDS.put("interactions", "взаимодействие другими лекарствами препаратами совместимость алкоголь");
        RU_KEYWORDS.put("overdose", "передозировка симптомы передозировки много выпил");
        RU_KEYWORDS.put("special_warnings", "особые указания специальные предупреждения меры предосторожности");
        RU_KEYWORDS.put("driving", "вождение управлять автомобилем машиной транспортным средством механизмами");
        RU_KEYWORDS.put("storage", "условия хранения хранить температура");
        RU_KEYWORDS.put("shelf_life", "срок хранения срок годности");
        RU_KEYWORDS.put("dispensing", "условия отпуска рецепт без рецепта");
        RU_KEYWORDS.put("manufacturer", "производитель изготовитель сведения о производителе");
        RU_KEYWORDS.put("holder", "держатель регистрационного удостоверения");
    }

    public static String keywords(String type, String lang) {
        if (!"ru".equals(lang)) return "";
        return RU_KEYWORDS.getOrDefault(type, "");
    }
}
