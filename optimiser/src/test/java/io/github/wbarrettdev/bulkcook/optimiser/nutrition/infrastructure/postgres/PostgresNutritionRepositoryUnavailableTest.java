package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.postgres;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FakeSource;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.IngredientNutrition;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

class PostgresNutritionRepositoryUnavailableTest {

    @Test
    void saysSoWhenTheDatabaseCannotBeReached() {
        DriverManagerDataSource nowhere =
                new DriverManagerDataSource("jdbc:postgresql://localhost:1/none?connectTimeout=2", "nobody", "nothing");
        NutritionRepository repository = new PostgresNutritionRepository(
                JdbcClient.create(nowhere), new TransactionTemplate(new DataSourceTransactionManager(nowhere)));

        assertThatThrownBy(repository::table)
                .isInstanceOf(NutritionRepository.Unavailable.class)
                .hasMessageStartingWith("The nutrition tables can't be reached");
        assertThatThrownBy(() -> repository.add(new IngredientNutrition("kale", "g", 1, FakeSource.KALE, null)))
                .as("a transaction that can't be opened is the same failure as a query that can't be run")
                .isInstanceOf(NutritionRepository.Unavailable.class);
    }
}
