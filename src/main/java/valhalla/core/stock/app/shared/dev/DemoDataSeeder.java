package valhalla.core.stock.app.shared.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

/**
 * Semeia os dados de demonstração apenas quando o perfil {@code dev} está ativo.
 *
 * <p>O seed vive fora do Flyway de propósito: dados de demonstração não são
 * migração de schema. Enquanto ele era uma migração repetível em
 * {@code db/demo}, o histórico do Flyway passava a conter um registro que não
 * existe no perfil padrão, e o boot falhava com
 * <em>"Detected applied migration not resolved locally"</em>.
 *
 * <p>Como o script é idempotente ({@code ON CONFLICT DO NOTHING} /
 * {@code WHERE NOT EXISTS}), executá-lo a cada inicialização não duplica dados
 * e reflete edições no arquivo sem necessidade de {@code flyway repair}.
 */
@Component
@Profile("dev")
class DemoDataSeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String SCRIPT = "db/demo/dados_demonstracao.sql";

    private final DataSource dataSource;

    DemoDataSeeder(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource(SCRIPT));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());

        LOG.info("Perfil dev ativo: aplicando dados de demonstração ({})", SCRIPT);
        DatabasePopulatorUtils.execute(populator, dataSource);
        LOG.info("Dados de demonstração aplicados com sucesso");
    }
}
