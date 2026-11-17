package io.github.wbarrettdev.bulkcook.knowledge.pairing;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingGraph.Avoidance;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingGraph.Combination;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingGraph.IngredientNotes;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingGraph.Pairing;
import java.util.List;

/** Everything worth saying about what goes with a dish. */
public record PairingAdvice(
        Dish dish,
        List<Combination> combinations,
        int combinationsLeftOut,
        List<Suggestion> suggestions,
        List<String> suggestionsLeftOut,
        List<Avoidance> avoidances,
        List<IngredientNotes> notes) {

    public PairingAdvice {
        combinations = List.copyOf(combinations);
        suggestions = List.copyOf(suggestions);
        suggestionsLeftOut = List.copyOf(suggestionsLeftOut);
        avoidances = List.copyOf(avoidances);
        notes = List.copyOf(notes);
    }

    static PairingAdvice nothingFor(Dish dish) {
        return new PairingAdvice(dish, List.of(), 0, List.of(), List.of(), List.of(), List.of());
    }

    /**
     * The dish being asked about. Each ingredient has one name to show. Its aliases are every name
     * the graph has for it, such as "lemon" for "lemons", and are what the graph is searched with.
     */
    public record Dish(List<String> ingredients, List<String> aliases, List<UnknownIngredient> unknown) {
        public Dish {
            ingredients = List.copyOf(ingredients);
            aliases = List.copyOf(aliases);
            unknown = List.copyOf(unknown);
        }

        public boolean hasNoIngredients() {
            return ingredients.isEmpty();
        }

        public boolean hasSeveralIngredients() {
            return ingredients.size() > 1;
        }
    }

    /** An ingredient the graph has no name for, with what was used instead or names close to it. */
    public record UnknownIngredient(String asked, List<String> usedInstead, List<String> closeNames) {
        public UnknownIngredient {
            usedInstead = List.copyOf(usedInstead);
            closeNames = List.copyOf(closeNames);
        }
    }

    /** An addition worth making to the dish, its score, and the pairings behind it. */
    public record Suggestion(String name, int score, boolean goEasy, List<Pairing> reasons) {
        public Suggestion {
            reasons = List.copyOf(reasons);
        }
    }
}
