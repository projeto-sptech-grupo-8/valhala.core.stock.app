package valhalla.core.stock.app.shared.dev;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Garante que os dados de demonstração só possam ser semeados com o perfil
 * {@code dev} ativo e que o script esteja empacotado na aplicação.
 */
class DemoDataSeederTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(DataSource.class, JdbcDataSource::new)
            .withUserConfiguration(DemoDataSeeder.class);

    @Test
    void naoDeveCriarSeederSemPerfilDev() {
        runner.run(context -> assertNull(context.getBeanProvider(DemoDataSeeder.class).getIfAvailable()));
    }

    @Test
    void naoDeveCriarSeederComOutroPerfil() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertNull(context.getBeanProvider(DemoDataSeeder.class).getIfAvailable()));
    }

    @Test
    void deveCriarSeederComPerfilDev() {
        runner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertNotNull(context.getBeanProvider(DemoDataSeeder.class).getIfAvailable()));
    }

    @Test
    void deveEmpacotarOScriptDeDemonstracao() {
        assertTrue(new ClassPathResource("db/demo/dados_demonstracao.sql").exists());
    }
}
