package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** A reference source holding the records a test gives it. */
public record FakeSource(Source source, List<FoodRecord> records) implements FoodSource {

    public static final FoodRecord KALE = new FoodRecord(
            new FoodReference(Source.COFID, "13-234"),
            "Curly kale, raw",
            "",
            new FoodValues(33.0, 3.4, 1.6, 1.3, 4.1, 0.11),
            FiveADayCategory.VEG,
            "");

    /** A record with no salt value, so it can't become a row. */
    public static final FoodRecord MYSTERY_POWDER = new FoodRecord(
            new FoodReference(Source.COFID, "99-001"),
            "Powder, mystery",
            "",
            new FoodValues(350.0, 10.0, 2.0, 70.0, null, null),
            null,
            "");

    public static FakeSource cofid() {
        return new FakeSource(Source.COFID, List.of(KALE, MYSTERY_POWDER));
    }

    @Override
    public List<FoodRecord> search(String query) {
        return records.stream()
                .filter(record -> record.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                .toList();
    }

    @Override
    public Optional<FoodRecord> find(String id) {
        return records.stream()
                .filter(record -> record.reference().id().equals(id))
                .findFirst();
    }
}
