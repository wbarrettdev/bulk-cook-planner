package io.github.wbarrettdev.bulkcook.knowledge.pairing.neo4j;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingGraph;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.QueryConfig;
import org.neo4j.driver.Record;
import org.neo4j.driver.RoutingControl;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.stereotype.Repository;

/** The pairing graph as stored in Neo4j. The only place that knows Cypher. */
@Repository
@RequiredArgsConstructor
class Neo4jPairingGraph implements PairingGraph {

    private static final QueryConfig READ_ONLY =
            QueryConfig.builder().withRouting(RoutingControl.READ).build();

    private static final String INGREDIENTS_NAMED = """
      MATCH (i) WHERE i.name IN $spellings
      OPTIONAL MATCH (i)-[:KIND_OF]->(k) WHERE coalesce(k.is_entry, false)
      RETURN i.name AS name, coalesce(i.is_entry, false) AS entry, collect(k.name) AS kinds
      """;

    private static final String NAMES_CONTAINING = """
      MATCH (i) WHERE i.name CONTAINS $fragment
      RETURN i.name AS name
      ORDER BY coalesce(i.is_entry, false) DESC, size(i.name)
      LIMIT 8
      """;

    private static final String PARTNERS = """
      MATCH (i)-[p:PAIRS_WITH]->(c) WHERE i.name IN $dish AND NOT c.name IN $dish
      OPTIONAL MATCH (c)-[:KIND_OF]->(k)
      RETURN c.name AS name,
             'Cuisine' IN labels(c) AS cuisine,
             collect(DISTINCT k.name) AS kinds,
             collect(DISTINCT {ingredient: i.name, strength: p.strength, note: p.note}) AS pairings
      """;

    private static final String COMBINATIONS = """
      MATCH (i)-[:IN_SET]->(s:Set) WHERE i.name IN $dish
      WITH s, collect(DISTINCT i.name) AS have WHERE size(have) >= $atLeast
      MATCH (m)-[:IN_SET]->(s) WHERE NOT m.name IN $dish
      OPTIONAL MATCH (m)-[:KIND_OF]->(k)
      RETURN s.id AS set, have, collect(DISTINCT m.name) AS toAdd, collect(DISTINCT k.name) AS kinds
      """;

    private static final String AVOIDANCES = """
      MATCH (i)-[:AVOID]->(c) WHERE i.name IN $dish
      RETURN i.name AS ingredient, c.name AS partner
      """;

    private static final String NOTES = """
      MATCH (i) WHERE i.name IN $ingredients
      RETURN i.name AS ingredient, i.techniques AS techniques, i.season AS season,
             i.tips AS tips, i.see AS see
      """;

    private static final String PAIRINGS_BETWEEN = """
            MATCH (a)-[p:PAIRS_WITH]->(b)
            WHERE (a.name IN $these AND b.name IN $those) OR (a.name IN $those AND b.name IN $these)
            RETURN a.name AS ingredient, b.name AS partner, p.strength AS strength, p.note AS note
            """;

    private final Driver driver;

    @Override
    public List<Ingredient> ingredientsNamed(Collection<String> spellings) {
        return ask(INGREDIENTS_NAMED, Map.of("spellings", spellings)).stream()
                .map(row -> new Ingredient(text(row, "name"), row.get("entry").asBoolean(), texts(row, "kinds")))
                .toList();
    }

    @Override
    public List<String> namesContaining(String fragment) {
        return ask(NAMES_CONTAINING, Map.of("fragment", fragment)).stream()
                .map(row -> text(row, "name"))
                .toList();
    }

    @Override
    public List<Candidate> partnersOf(Collection<String> dish) {
        return ask(PARTNERS, Map.of("dish", dish)).stream()
                .map(Neo4jPairingGraph::candidate)
                .toList();
    }

    @Override
    public List<Combination> combinationsSharing(Collection<String> dish, int atLeast) {
        return ask(COMBINATIONS, Map.of("dish", dish, "atLeast", atLeast)).stream()
                .map(row ->
                        new Combination(texts(row, "have"), texts(row, "toAdd"), new HashSet<>(texts(row, "kinds"))))
                .toList();
    }

    @Override
    public List<Avoidance> avoidancesFor(Collection<String> dish) {
        return ask(AVOIDANCES, Map.of("dish", dish)).stream()
                .map(row -> new Avoidance(text(row, "ingredient"), text(row, "partner")))
                .toList();
    }

    @Override
    public List<IngredientNotes> notesFor(Collection<String> ingredients) {
        return ask(NOTES, Map.of("ingredients", ingredients)).stream()
                .map(row -> new IngredientNotes(
                        text(row, "ingredient"),
                        text(row, "techniques"),
                        text(row, "season"),
                        text(row, "tips"),
                        text(row, "see")))
                .toList();
    }

    @Override
    public List<Pairing> pairingsBetween(Collection<String> these, Collection<String> those) {
        return ask(PAIRINGS_BETWEEN, Map.of("these", these, "those", those)).stream()
                .map(row -> new Pairing(
                        text(row, "ingredient"),
                        text(row, "partner"),
                        row.get("strength").asInt(),
                        text(row, "note")))
                .toList();
    }

    private List<Record> ask(String cypher, Map<String, Object> parameters) {
        try {
            return driver.executableQuery(cypher)
                    .withParameters(parameters)
                    .withConfig(READ_ONLY)
                    .execute()
                    .records();
        } catch (Neo4jException e) {
            throw new Unavailable(e);
        }
    }

    private static Candidate candidate(Record row) {
        String name = text(row, "name");
        List<Pairing> pairings = row.get("pairings")
                .asList(link -> new Pairing(
                        link.get("ingredient").asString(),
                        name,
                        link.get("strength").asInt(),
                        link.get("note").asString("")));
        return new Candidate(name, row.get("cuisine").asBoolean(), new HashSet<>(texts(row, "kinds")), pairings);
    }

    private static String text(Record row, String column) {
        return row.get(column).asString("");
    }

    private static List<String> texts(Record row, String column) {
        return row.get(column).asList(Value::asString);
    }
}
