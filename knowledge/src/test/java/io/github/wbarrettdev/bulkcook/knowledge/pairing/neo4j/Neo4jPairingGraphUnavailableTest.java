package io.github.wbarrettdev.bulkcook.knowledge.pairing.neo4j;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.PairingGraph;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

class Neo4jPairingGraphUnavailableTest {

    private static final String NOTHING_LISTENS_HERE = "bolt://localhost:1";

    @Test
    void saysSoPlainlyWhenTheGraphCannotBeReached() {
        Config failFast = Config.builder()
                .withConnectionTimeout(1, TimeUnit.SECONDS)
                .withMaxTransactionRetryTime(1, TimeUnit.SECONDS)
                .build();
        try (Driver driver = GraphDatabase.driver(NOTHING_LISTENS_HERE, AuthTokens.none(), failFast)) {
            PairingGraph graph = new Neo4jPairingGraph(driver);

            assertThatThrownBy(() -> graph.ingredientsNamed(List.of("salmon")))
                    .isInstanceOf(PairingGraph.Unavailable.class)
                    .hasMessageContaining("can't be reached");
        }
    }
}
