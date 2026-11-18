package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.http;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.config.FoodSourcesProperties;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

/** Asks a source again when it says it is busy, waiting a little longer each time. */
@Component
public class RetryWhenBusy implements ClientHttpRequestInterceptor {

    private static final int TRIES = 3;
    private static final int TOO_MANY_REQUESTS = 429;

    private final Duration pause;

    public RetryWhenBusy(FoodSourcesProperties sources) {
        this.pause = sources.retryDelay();
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        for (int attempt = 1; ; attempt++) {
            ClientHttpResponse response = execution.execute(request, body);
            if (attempt == TRIES || !isBusy(response.getStatusCode())) {
                return response;
            }
            response.close();
            waitFor(pause.multipliedBy(attempt));
        }
    }

    private static boolean isBusy(HttpStatusCode status) {
        return status.value() == TOO_MANY_REQUESTS || status.is5xxServerError();
    }

    private static void waitFor(Duration time) throws IOException {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to ask again", e);
        }
    }
}
