package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.SourceSettings;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class RetryWhenBusyTest {

    private static final String ADDRESS = "https://source.test/food";

    private final RestClient.Builder builder = RestClient.builder().requestInterceptor(SourceSettings.RETRY);
    private final MockRestServiceServer source =
            MockRestServiceServer.bindTo(builder).build();
    private final RestClient http = builder.build();

    @Test
    void asksAgainUntilABusySourceAnswers() {
        source.expect(requestTo(ADDRESS)).andRespond(withTooManyRequests());
        source.expect(requestTo(ADDRESS)).andRespond(withServerError());
        source.expect(requestTo(ADDRESS)).andRespond(withSuccess("ready", MediaType.TEXT_PLAIN));

        assertThat(http.get().uri(ADDRESS).retrieve().body(String.class)).isEqualTo("ready");
        source.verify();
    }

    @Test
    void givesUpAfterThreeTries() {
        source.expect(times(3), requestTo(ADDRESS)).andRespond(withServerError());

        assertThatThrownBy(() -> http.get().uri(ADDRESS).retrieve().body(String.class))
                .isInstanceOf(HttpServerErrorException.class);
        source.verify();
    }

    @Test
    void doesNotRepeatARequestTheSourceRefused() {
        source.expect(times(1), requestTo(ADDRESS)).andRespond(withBadRequest());

        assertThatThrownBy(() -> http.get().uri(ADDRESS).retrieve().body(String.class))
                .isInstanceOf(HttpClientErrorException.class);
        source.verify();
    }
}
