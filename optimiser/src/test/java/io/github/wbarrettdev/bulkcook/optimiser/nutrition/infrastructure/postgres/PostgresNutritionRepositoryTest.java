package io.github.wbarrettdev.bulkcook.optimiser.nutrition.infrastructure.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.wbarrettdev.bulkcook.optimiser.Fixtures;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FakeSource;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.FiveADayCategory;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.IngredientNutrition;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository.Outcome;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionRepository.Stored;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable;
import io.github.wbarrettdev.bulkcook.optimiser.nutrition.domain.NutritionTable.PerUnit;
import io.github.wbarrettdev.bulkcook.optimiser.week.application.WeekOptimiser;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Runs the migration and the queries against a real PostgreSQL holding the tables kept with the tests. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PostgresNutritionRepositoryTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private NutritionTable sample;

    @Autowired
    private NutritionRepository repository;

    @Autowired
    private WeekOptimiser optimiser;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void storeTheSample() {
        sample = StoredSample.into(jdbc);
    }

    @Test
    void readsBackEveryStoredRow() {
        NutritionTable stored = repository.table();

        assertThat(stored.rows()).containsExactlyInAnyOrderElementsOf(sample.rows());
        assertThat(stored.saltPer100g()).isEqualTo(sample.saltPer100g());
        assertThat(stored.fibrePer100g()).isEqualTo(sample.fibrePer100g());
        assertThat(stored.categories()).isEqualTo(sample.categories());
    }

    @Test
    void solvesTheWeekFromTheStoredTables() {
        assertThat(optimiser.optimise(Fixtures.week(), Fixtures.targets()))
                .isEqualTo(Fixtures.optimiser().optimise(Fixtures.week(), Fixtures.targets()));
    }

    @Test
    void storesAnIngredientWhereTheSolverFindsIt() {
        Stored stored = repository.add(new IngredientNutrition("kale", "g", 1, FakeSource.KALE, FiveADayCategory.VEG));

        assertThat(stored).isEqualTo(new Stored(Outcome.WRITTEN, Outcome.WRITTEN, Outcome.WRITTEN, Outcome.WRITTEN));
        PerUnit kale =
                (PerUnit) repository.table().withPortionsOf(Map.of("veg", 80.0)).perUnit("recipe", "kale", "g", "");
        assertThat(kale.kcal()).isEqualTo(0.33);
        assertThat(kale.salt()).isCloseTo(0.0011, within(1e-12));
        assertThat(kale.fibre()).isCloseTo(0.041, within(1e-12));
        assertThat(kale.vegAndFruitPortions()).isCloseTo(1 / 80.0, within(1e-12));
        assertThat(kale.table()).isEqualTo("kale (g): cofid:13-234 Curly kale, raw");
    }

    @Test
    void neverChangesARowThatIsAlreadyThere() {
        NutritionTable before = repository.table();

        Stored stored =
                repository.add(new IngredientNutrition("onion", "g", 1, FakeSource.KALE, FiveADayCategory.FRUIT));

        assertThat(stored)
                .isEqualTo(new Stored(
                        Outcome.ALREADY_THERE, Outcome.ALREADY_THERE, Outcome.ALREADY_THERE, Outcome.ALREADY_THERE));
        assertThat(repository.table()).isEqualTo(before);
    }

    @Test
    void addsAnotherUnitBesideTheOneThatIsThere() {
        Stored stored = repository.add(new IngredientNutrition("onion", "each", 150, FakeSource.KALE, null));

        assertThat(stored)
                .isEqualTo(
                        new Stored(Outcome.WRITTEN, Outcome.ALREADY_THERE, Outcome.ALREADY_THERE, Outcome.NOT_GIVEN));
        assertThat(repository.table().rows()).hasSize(sample.rows().size() + 1);
    }

    @Test
    void refusesAnIngredientThatIsNotLowerCase() {
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO ingredient_salt (ingredient, salt_g_per_100g) VALUES (?, ?)")
                        .params("Sea Salt", 100)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesACategoryThatFiveADayDoesNotHave() {
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO ingredient_five_a_day VALUES (?, ?)")
                        .params("potato", "starch")
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
