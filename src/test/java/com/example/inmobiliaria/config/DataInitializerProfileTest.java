package com.example.inmobiliaria.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DataInitializerProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DataInitializer.class);

    @Test
    void doesNotLoadDemoAccountsWithoutExplicitDemoProfile() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(DataInitializer.class);
            assertThat(context).doesNotHaveBean(CommandLineRunner.class);
        });
    }

    @Test
    void productionProfileDisablesDemoAccountsEvenIfDemoIsAlsoActive() {
        contextRunner.withPropertyValues("spring.profiles.active=prod,demo").run(context -> {
            assertThat(context).doesNotHaveBean(DataInitializer.class);
            assertThat(context).doesNotHaveBean(CommandLineRunner.class);
        });
    }
}
