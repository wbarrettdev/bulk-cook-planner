package io.github.wbarrettdev.bulkcook.knowledge.pairing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A small pairing graph held in memory, so the advice can be tested without a database. */
public final class FakeGraph implements PairingGraph {

    private final Set<String> names = new LinkedHashSet<>();
    private final Set<String> entries = new HashSet<>();
    private final Set<String> cuisines = new HashSet<>();
    private final Map<String, Set<String>> kinds = new HashMap<>();
    private final List<Pairing> pairings = new ArrayList<>();
    private final List<List<String>> combinations = new ArrayList<>();
    private final List<Avoidance> avoidances = new ArrayList<>();

    public FakeGraph entry(String name) {
        names.add(name);
        entries.add(name);
        return this;
    }

    public FakeGraph cuisine(String name) {
        names.add(name);
        cuisines.add(name);
        return this;
    }

    public FakeGraph kindOf(String kind, String of) {
        names.add(kind);
        names.add(of);
        kinds.computeIfAbsent(kind, k -> new HashSet<>()).add(of);
        return this;
    }

    public FakeGraph pairs(String ingredient, String partner, int strength) {
        names.add(ingredient);
        names.add(partner);
        pairings.add(new Pairing(ingredient, partner, strength, ""));
        return this;
    }

    public FakeGraph combination(String... members) {
        names.addAll(List.of(members));
        combinations.add(List.of(members));
        return this;
    }

    public FakeGraph avoid(String ingredient, String partner) {
        avoidances.add(new Avoidance(ingredient, partner));
        return this;
    }

    @Override
    public List<Ingredient> ingredientsNamed(Collection<String> spellings) {
        return spellings.stream()
                .filter(names::contains)
                .map(name -> new Ingredient(name, entries.contains(name), entryKindsOf(name)))
                .toList();
    }

    @Override
    public List<String> namesContaining(String fragment) {
        return names.stream().filter(name -> name.contains(fragment)).toList();
    }

    @Override
    public List<Candidate> partnersOf(Collection<String> dish) {
        Map<String, List<Pairing>> byPartner = new LinkedHashMap<>();
        pairings.stream()
                .filter(pairing -> dish.contains(pairing.ingredient()) && !dish.contains(pairing.partner()))
                .forEach(p -> byPartner
                        .computeIfAbsent(p.partner(), k -> new ArrayList<>())
                        .add(p));
        return byPartner.entrySet().stream()
                .map(e -> new Candidate(e.getKey(), cuisines.contains(e.getKey()), kindsOf(e.getKey()), e.getValue()))
                .toList();
    }

    @Override
    public List<Combination> combinationsSharing(Collection<String> dish, int atLeast) {
        List<Combination> shared = new ArrayList<>();
        for (List<String> members : combinations) {
            List<String> have = members.stream().filter(dish::contains).toList();
            List<String> toAdd =
                    members.stream().filter(member -> !dish.contains(member)).toList();
            if (have.size() >= atLeast && !toAdd.isEmpty()) {
                Set<String> kindsOfToAdd = new HashSet<>();
                toAdd.forEach(member -> kindsOfToAdd.addAll(kindsOf(member)));
                shared.add(new Combination(have, toAdd, kindsOfToAdd));
            }
        }
        return shared;
    }

    @Override
    public List<Avoidance> avoidancesFor(Collection<String> dish) {
        return avoidances.stream()
                .filter(avoidance -> dish.contains(avoidance.ingredient()))
                .toList();
    }

    @Override
    public List<IngredientNotes> notesFor(Collection<String> ingredients) {
        return ingredients.stream()
                .map(name -> new IngredientNotes(name, "", "", "", ""))
                .toList();
    }

    @Override
    public List<Pairing> pairingsBetween(Collection<String> these, Collection<String> those) {
        return pairings.stream()
                .filter(p -> (these.contains(p.ingredient()) && those.contains(p.partner()))
                        || (those.contains(p.ingredient()) && these.contains(p.partner())))
                .toList();
    }

    private Set<String> kindsOf(String name) {
        return kinds.getOrDefault(name, Set.of());
    }

    private List<String> entryKindsOf(String name) {
        return kindsOf(name).stream().filter(entries::contains).toList();
    }
}
