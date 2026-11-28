package valhalla.core.stock.app.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Instância singleton de PostgreSQL embarcado compartilhada pelas suítes de integração da JVM.
 * Executa {@code db/script.sql} na subida e deixa o Flyway aplicar {@code V1..V30} com {@code ddl-auto=validate}.
 */
public final class EmbeddedPostgresTestDatabase {

    private static final EmbeddedPostgres POSTGRES = iniciarPostgres();

    private EmbeddedPostgresTestDatabase() {
    }

    public static void registrarPropriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("spring.flyway.baseline-on-migrate", () -> "true");
        registry.add("spring.flyway.baseline-version", () -> "0");
    }

    public static void limparTabelasOperacionais(JdbcTemplate jdbcTemplate) {
        List<String> tabelas = jdbcTemplate.queryForList("""
                SELECT tablename
                FROM pg_tables
                WHERE schemaname = 'public'
                  AND tablename NOT IN ('funcionalidade', 'flyway_schema_history')
                ORDER BY tablename
                """, String.class);
        if (tabelas.isEmpty()) {
            return;
        }
        String listaTabelas = tabelas.stream()
                .map(nome -> "\"" + nome + "\"")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("TRUNCATE TABLE " + listaTabelas + " RESTART IDENTITY CASCADE");
    }

    private static EmbeddedPostgres iniciarPostgres() {
        try {
            EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    postgres.close();
                } catch (IOException ignorada) {
                    // encerramento da JVM
                }
            }));
            try (Connection conexao = postgres.getPostgresDatabase().getConnection();
                 Statement comando = conexao.createStatement()) {
                comando.execute(new ClassPathResource("db/script.sql").getContentAsString(StandardCharsets.UTF_8));
            }
            return postgres;
        } catch (IOException excecao) {
            throw new UncheckedIOException(excecao);
        } catch (SQLException excecao) {
            throw new IllegalStateException("Falha ao criar o schema de teste a partir de db/script.sql", excecao);
        }
    }
}
