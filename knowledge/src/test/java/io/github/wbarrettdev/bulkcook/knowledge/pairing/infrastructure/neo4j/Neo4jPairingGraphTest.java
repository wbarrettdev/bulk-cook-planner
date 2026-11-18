package io.github.wbarrettdev.bulkcook.knowledge.pairing.infrastructure.neo4j;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.Avoidance;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.Candidate;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.Combination;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.Ingredient;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.IngredientNotes;
import io.github.wbarrettdev.bulkcook.knowledge.pairing.domain.PairingGraph.Pairing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.utility.MountableFile;

/** Runs the queries against a real Neo4j holding the sample pairings. */
@Testcontainers(disabledWithoutDocker = true)
class Neo4jPairingGraphTest {

    private static final Path SAMPLE = Path.of("..", "samples", "pairings");

    @Container
    private static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5-community")
            .withoutAuthentication()
            .withCopyFileToContainer(MountableFile.forHostPath(SAMPLE), "/var/lib/neo4j/import");

    private static Driver driver;
    private static Neo4jPairingGraph graph;

    @BeforeAll
    static void loadTheSample() throws IOException {
        driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.none());
        for (String statement : statementsIn(SAMPLE.resolve("load.cypher"))) {
            driver.executableQuery(statement).execute();
        }
        graph = new Neo4jPairingGraph(driver);
    }

    @AfterAll
    static void closeTheDriver() {
        driver.close();
    }

    @Test
    void findsANameUnderAnyOfItsSpellings() {
        assertThat(graph.ingredientsNamed(List.of("salmons", "salmon")))
                .containsExactly(new Ingredient("salmon", true, List.of()));
    }

    @Test
    void reportsTheEntryANameIsAKindOf() {
        assertThat(graph.ingredientsNamed(List.of("cherry tomatoes")))
                .containsExactly(new Ingredient("cherry tomatoes", false, List.of("tomatoes")));
    }

    @Test
    void findsNamesContainingAFragment() {
        assertThat(graph.namesContaining("tomat")).contains("tomatoes", "cherry tomatoes");
    }

    @Test
    void reportsAPartnerWithItsStrengthAndNote() {
        Candidate mustard = partnerOf("salmon", "mustard");

        assertThat(mustard.pairings()).containsExactly(new Pairing("salmon", "mustard", 2, "esp. Dijon"));
    }

    @Test
    void gathersEveryPairingThatNamesTheSamePartner() {
        Candidate dill = graph.partnersOf(List.of("salmon", "potatoes")).stream()
                .filter(candidate -> candidate.name().equals("dill"))
                .findFirst()
                .orElseThrow();

        assertThat(dill.combinedStrength()).isEqualTo(5);
    }

    @Test
    void leavesTheDishItselfOutOfItsPartners() {
        assertThat(graph.partnersOf(List.of("salmon", "dill")))
                .extracting(Candidate::name)
                .doesNotContain("salmon", "dill");
    }

    @Test
    void saysWhenAPartnerIsACuisine() {
        assertThat(partnerOf("basil", "italian cuisine").isCuisine()).isTrue();
        assertThat(partnerOf("basil", "garlic").isCuisine()).isFalse();
    }

    @Test
    void reportsWhatAPartnerIsAKindOf() {
        assertThat(partnerOf("beef", "porcini mushrooms").kindOf()).containsExactly("mushrooms");
    }

    @Test
    void keepsCombinationsThatShareTheSameIngredientsApart() {
        List<Combination> sets = graph.combinationsSharing(List.of("tomatoes"), 1);

        assertThat(sets).hasSize(3);
        assertThat(sets).allSatisfy(set -> assertThat(set.missing()).hasSize(3));
    }

    @Test
    void findsOnlyCombinationsThatShareEnoughOfTheDish() {
        List<Combination> sets = graph.combinationsSharing(List.of("chicken", "lemon"), 2);

        assertThat(sets).hasSize(1);
        assertThat(sets.getFirst().missing()).containsExactlyInAnyOrder("garlic", "thyme");
    }

    @Test
    void reportsWhatToAvoid() {
        assertThat(graph.avoidancesFor(List.of("dill")))
                .containsExactlyInAnyOrder(new Avoidance("dill", "rosemary"), new Avoidance("dill", "cinnamon"));
    }

    @Test
    void reportsAnIngredientsNotes() {
        assertThat(graph.notesFor(List.of("ginger")))
                .containsExactly(new IngredientNotes(
                        "ginger", "grate, fry, infuse", "all year", "Fresh for brightness, dried for warmth.", ""));
    }

    @Test
    void findsPairingsBetweenTwoIngredientsInBothDirections() {
        assertThat(graph.pairingsBetween(List.of("salmon"), List.of("dill")))
                .containsExactlyInAnyOrder(new Pairing("salmon", "dill", 4, ""), new Pairing("dill", "salmon", 4, ""));
    }

    @Test
    void findsNoPairingsBetweenIngredientsThatAreNotPaired() {
        assertThat(graph.pairingsBetween(List.of("oats"), List.of("salmon"))).isEmpty();
    }

    private static Candidate partnerOf(String ingredient, String partner) {
        return graph.partnersOf(Set.of(ingredient)).stream()
                .filter(candidate -> candidate.name().equals(partner))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> statementsIn(Path script) throws IOException {
        String withoutComments = Files.readAllLines(script).stream()
                .filter(line -> !line.strip().startsWith("//"))
                .collect(Collectors.joining("\n"));
        return Arrays.stream(withoutComments.split(";"))
                .map(String::strip)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }
}
