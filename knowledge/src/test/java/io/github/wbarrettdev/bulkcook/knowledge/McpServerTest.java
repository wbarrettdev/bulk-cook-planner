package io.github.wbarrettdev.bulkcook.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.wbarrettdev.bulkcook.knowledge.pairing.infrastructure.neo4j.SampleGraph;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;

/** Calls the tools the way a client does: over MCP, through the running server and a real graph. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class McpServerTest {

    @Container
    private static final Neo4jContainer NEO4J = SampleGraph.container();

    @LocalServerPort
    private int port;

    private McpSyncClient client;

    @DynamicPropertySource
    static void pointTheServiceAtTheSampleGraph(DynamicPropertyRegistry settings) {
        settings.add("spring.neo4j.uri", NEO4J::getBoltUrl);
    }

    @BeforeAll
    static void loadTheSample() {
        try (Driver driver = GraphDatabase.driver(NEO4J.getBoltUrl(), AuthTokens.none())) {
            SampleGraph.loadInto(driver);
        }
    }

    @BeforeEach
    void connect() {
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                        .endpoint("/mcp")
                        .build())
                .requestTimeout(Duration.ofSeconds(60))
                .build();
        client.initialize();
    }

    @AfterEach
    void disconnect() {
        client.closeGracefully();
    }

    @Test
    void offersBothTools() {
        assertThat(client.listTools().tools())
                .extracting(Tool::name)
                .containsExactlyInAnyOrder("suggest_additions", "check_pairings");
    }

    @Test
    void suggestsAdditionsForADishSentOverTheWire() {
        CallToolResult result =
                client.callTool(new CallToolRequest("suggest_additions", Map.of("ingredients", List.of("salmon"))));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(text(result)).contains("Pairings for: salmon", "dill");
    }

    @Test
    void confirmsAPairingSentOverTheWire() {
        CallToolResult result = client.callTool(new CallToolRequest(
                "check_pairings", Map.of("ingredient", "salmon", "partners", List.of("dill", "cinnamon"))));

        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(text(result)).contains("dill: yes");
    }

    @Test
    void refusesACallThatLeavesOutWhatTheToolNeeds() {
        CallToolResult result = client.callTool(new CallToolRequest("suggest_additions", Map.of("limit", 3)));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).contains("ingredients");
    }

    @Test
    void saysWhatToChangeWhenAskedForFewerThanOneAddition() {
        CallToolResult result = client.callTool(
                new CallToolRequest("suggest_additions", Map.of("ingredients", List.of("salmon"), "limit", 0)));

        assertThat(result.isError()).isTrue();
        assertThat(text(result)).isEqualTo("The limit must be at least 1, not 0.");
    }

    private static String text(CallToolResult result) {
        return ((TextContent) result.content().getFirst()).text();
    }
}
