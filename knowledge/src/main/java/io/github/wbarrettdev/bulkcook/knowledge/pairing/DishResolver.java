package io.github.wbarrettdev.bulkcook.knowledge.pairing;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingAdvice.Dish;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingAdvice.UnknownIngredient;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingGraph.Ingredient;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;

/** Matches the ingredients someone asked about to the names the graph uses. */
@RequiredArgsConstructor
final class DishResolver {

    private final PairingGraph graph;

    Dish resolve(List<String> asked) {
        List<Resolved> resolved = asked.stream()
                .map(DishResolver::normalised)
                .filter(name -> !name.isEmpty())
                .map(this::resolve)
                .toList();
        return new Dish(
                resolved.stream()
                        .flatMap(r -> r.ingredient().stream())
                        .distinct()
                        .toList(),
                resolved.stream().flatMap(r -> r.aliases().stream()).distinct().toList(),
                resolved.stream().flatMap(r -> r.unknown().stream()).toList());
    }

    private Resolved resolve(String name) {
        List<Ingredient> matches = graph.ingredientsNamed(spellingsOf(name));
        if (!matches.isEmpty()) {
            return new Resolved(Optional.of(nameToShow(matches)), aliasesOf(matches), Optional.empty());
        }
        List<Ingredient> entries = entriesAmongTheWordsOf(name);
        Optional<UnknownIngredient> unknown = Optional.of(unknownIngredient(name, entries));
        if (entries.isEmpty()) {
            return new Resolved(Optional.empty(), List.of(), unknown);
        }
        return new Resolved(Optional.of(nameToShow(entries)), aliasesOf(entries), unknown);
    }

    /** What one asked-for ingredient came to: a name to show, its aliases, or a note that it is unknown. */
    private record Resolved(Optional<String> ingredient, List<String> aliases, Optional<UnknownIngredient> unknown) {}

    static String normalised(String name) {
        return name.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** The graph has "lemon" in one place and "lemons" in another. These are the forms to try. */
    static List<String> spellingsOf(String name) {
        Set<String> variants = new LinkedHashSet<>();
        variants.add(name);
        variants.add(name + "s");
        if (name.endsWith("ies")) {
            variants.add(name.substring(0, name.length() - 3) + "y");
        }
        if (name.endsWith("oes")) {
            variants.add(name.substring(0, name.length() - 2));
        }
        if (name.endsWith("s")) {
            variants.add(name.substring(0, name.length() - 1));
        }
        return List.copyOf(variants);
    }

    /** "salmon fillets" is not in the graph, but "salmon" is. */
    private List<Ingredient> entriesAmongTheWordsOf(String name) {
        return Arrays.stream(name.split(" "))
                .filter(word -> word.length() > 2)
                .flatMap(word -> graph.ingredientsNamed(spellingsOf(word)).stream())
                .filter(Ingredient::hasOwnEntry)
                .toList();
    }

    private UnknownIngredient unknownIngredient(String name, List<Ingredient> usedInstead) {
        List<String> used =
                usedInstead.stream().map(Ingredient::name).distinct().toList();
        List<String> close = used.isEmpty() ? graph.namesContaining(lastWordStem(name)) : List.of();
        return new UnknownIngredient(name, used, close);
    }

    private static String lastWordStem(String name) {
        String lastWord = name.substring(name.lastIndexOf(' ') + 1);
        return lastWord.endsWith("s") ? lastWord.substring(0, lastWord.length() - 1) : lastWord;
    }

    private static String nameToShow(List<Ingredient> matches) {
        return entriesOf(matches).findFirst().orElse(matches.getFirst().name());
    }

    /** Every name the graph has for the ingredient: the ones that matched, and its entry's spellings. */
    private List<String> aliasesOf(List<Ingredient> matches) {
        Set<String> names = new LinkedHashSet<>();
        matches.forEach(match -> names.add(match.name()));
        entriesOf(matches)
                .flatMap(entry -> graph.ingredientsNamed(spellingsOf(entry)).stream())
                .forEach(spelling -> names.add(spelling.name()));
        return List.copyOf(names);
    }

    /** Salmon has its own entry. Chicken thighs do not, so they borrow the entry for chicken. */
    private static Stream<String> entriesOf(List<Ingredient> matches) {
        List<String> ownEntries = matches.stream()
                .filter(Ingredient::hasOwnEntry)
                .map(Ingredient::name)
                .toList();
        if (!ownEntries.isEmpty()) {
            return ownEntries.stream();
        }
        return matches.stream().flatMap(match -> match.kindOf().stream()).distinct();
    }
}
