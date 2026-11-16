package kz.medbox.core;

import kz.medbox.core.Model.Leaflet;
import kz.medbox.core.Model.Section;
import kz.medbox.core.Model.Variant;

import java.util.ArrayList;
import java.util.List;

/** Small in-code catalogue with look-alike pairs, mirroring data/catalogue.json (SAMPLE data). */
final class TestData {

    static final String[][] SECTIONS = {
            {"composition", "Состав"}, {"form", "Лекарственная форма"}, {"indications", "Показания к применению"},
            {"contraindications", "Противопоказания"}, {"children", "Применение в педиатрии"},
            {"pregnancy", "Применение в период беременности или лактации"}, {"dosing", "Способ применения и дозы"},
            {"side_effects", "Побочные действия"}, {"interactions", "Лекарственные взаимодействия"},
            {"overdose", "Передозировка"}, {"storage", "Условия хранения"}, {"shelf_life", "Срок хранения"},
            {"manufacturer", "Производитель"}};

    static Catalogue catalogue() {
        List<Variant> v = List.of(
                new Variant("para-500", "Парацетамол-Образец", "парацетамол", "500 мг", "таблетки", "Образец Фарм", "S1", "para", true),
                new Variant("para-200", "Парацетамол-Образец", "парацетамол", "200 мг", "таблетки", "Образец Фарм", "S2", "para", true),
                new Variant("ibu-200-tab", "Ибупрофен-Образец", "ибупрофен", "200 мг", "таблетки, покрытые пленочной оболочкой", "Образец Фарм", "S3", "ibu", true),
                new Variant("ibu-susp", "Ибупрофен-Образец", "ибупрофен", "100 мг/5 мл", "суспензия для приема внутрь", "Образец Фарм", "S4", "ibu", true),
                new Variant("ibu-200-tab-other", "Ибупрофен-Образец", "ибупрофен", "200 мг", "таблетки, покрытые пленочной оболочкой", "Другой Завод", "S5", "ibu", true));
        List<Leaflet> leaflets = new ArrayList<>();
        for (Variant x : v) leaflets.add(leaflet(x.id(), "ru"));
        return new Catalogue(v, leaflets);
    }

    static Leaflet leaflet(String id, String lang) {
        List<Section> s = new ArrayList<>();
        for (int i = 0; i < SECTIONS.length; i++) {
            s.add(new Section(id + ":" + lang + ":" + SECTIONS[i][0] + ":" + i, SECTIONS[i][0], SECTIONS[i][1],
                    "ОБРАЗЕЦ — тестовые данные. Официальный текст раздела «" + SECTIONS[i][1] + "»."));
        }
        return new Leaflet(id, lang, "SAMPLE", "2026-11-16", s);
    }
}
