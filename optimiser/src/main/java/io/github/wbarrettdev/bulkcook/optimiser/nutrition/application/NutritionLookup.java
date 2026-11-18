package io.github.wbarrettdev.bulkcook.optimiser.nutrition.application;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FiveADayCategory;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodRecord;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodReference;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FoodSource;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.IngredientNutrition;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.LookupProblem;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository.Stored;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.Entry;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.Source;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Finds a food's values in a reference source, and stores them as an ingredient's nutrition rows. */
@Service
public class NutritionLookup {

    private final Map<Source, FoodSource> sources;
    private final NutritionRepository nutrition;

    public NutritionLookup(List<FoodSource> sources, NutritionRepository nutrition) {
        this.sources = sources.stream().collect(toMap(FoodSource::source, identity()));
        this.nutrition = nutrition;
    }

    public List<FoodRecord> search(Source source, String query) {
        if (query == null || query.isBlank()) {
            throw new LookupProblem("Say which food to look for.");
        }
        return the(source).search(query.strip());
    }

    /** What the nutrition table already holds, for ingredients named with these words. */
    public List<Entry> storedIngredients(String words) {
        if (words == null || words.isBlank()) {
            throw new LookupProblem("Say which ingredient to look for.");
        }
        return nutrition.table().entriesNamedWith(words);
    }

    /** Reads the record again from its source, so the stored values are the source's and nobody's copy. */
    public Addition add(NewRow row) {
        FoodReference reference = row.record();
        FoodRecord record = the(reference.source())
                .find(reference.id())
                .orElseThrow(() -> new LookupProblem(reference.source().title() + " has no record " + reference
                        + ". Search again and use a record from the results."));
        IngredientNutrition ingredient =
                new IngredientNutrition(row.ingredient(), row.unit(), row.gramsPerUnit(), record, row.fiveADay());
        return new Addition(ingredient, nutrition.add(ingredient));
    }

    private FoodSource the(Source source) {
        FoodSource found = sources.get(source);
        if (found == null) {
            throw new LookupProblem(source.title() + " isn't set up in this service.");
        }
        return found;
    }

    /**
     * The row to add: which record to take the values from, and how recipes name and measure the
     * ingredient. The category is null when it doesn't count towards five a day.
     */
    public record NewRow(
            FoodReference record, String ingredient, String unit, double gramsPerUnit, FiveADayCategory fiveADay) {}

    /** The rows as they were built from the record, and what became of each. */
    public record Addition(IngredientNutrition ingredient, Stored stored) {}
}
