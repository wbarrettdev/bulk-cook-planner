package io.github.wbarrettdev.bulkcook.knowledge.pairing.infrastructure.neo4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.neo4j.driver.Driver;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.utility.MountableFile;

/** A Neo4j for tests, holding the sample pairings that ship with the repo. */
public final class SampleGraph {

    private static final Path SAMPLE = Path.of("..", "samples", "pairings");

    private SampleGraph() {}

    /** A container with the sample's files where Neo4j can load them from. */
    public static Neo4jContainer container() {
        return new Neo4jContainer("neo4j:5-community")
                .withoutAuthentication()
                .withCopyFileToContainer(MountableFile.forHostPath(SAMPLE), "/var/lib/neo4j/import");
    }

    /** Runs the sample's own load script, the one a person runs after cloning. */
    public static void loadInto(Driver driver) {
        statementsIn(SAMPLE.resolve("load.cypher"))
                .forEach(statement -> driver.executableQuery(statement).execute());
    }

    private static List<String> statementsIn(Path script) {
        try {
            String withoutComments = Files.readAllLines(script).stream()
                    .filter(line -> !line.strip().startsWith("//"))
                    .collect(Collectors.joining("\n"));
            return Arrays.stream(withoutComments.split(";"))
                    .map(String::strip)
                    .filter(statement -> !statement.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
