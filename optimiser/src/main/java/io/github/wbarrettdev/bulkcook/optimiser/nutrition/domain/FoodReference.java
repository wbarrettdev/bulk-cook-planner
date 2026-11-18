package io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain;

import java.util.Locale;

/** Names one record in one source. Written "cofid:13-234", which is also how a row cites it. */
public record FoodReference(Source source, String id) {

    public static FoodReference parse(String text) {
        String written = text == null ? "" : text.strip();
        int colon = written.indexOf(':');
        String id = colon < 0 ? "" : written.substring(colon + 1).strip();
        return Source.withKey(
                        colon < 0 ? "" : written.substring(0, colon).strip().toLowerCase(Locale.ROOT))
                .filter(source -> !id.isEmpty())
                .map(source -> new FoodReference(source, id))
                .orElseThrow(() -> new LookupProblem("\"" + written
                        + "\" doesn't name a record. Write it as the search gave it, such as cofid:13-234."));
    }

    @Override
    public String toString() {
        return source.key() + ":" + id;
    }
}
