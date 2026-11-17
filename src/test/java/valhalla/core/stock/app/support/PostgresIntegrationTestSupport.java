package valhalla.core.stock.app.support;

import com.jayway.jsonpath.JsonPath;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.FuncionalidadeRepository;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.ComposicaoDrinkEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.entity.UnidadeConsumoDrink;
import valhalla.core.stock.app.modules.estoque.repository.CategoriaRepository;
import valhalla.core.stock.app.modules.estoque.repository.ComposicaoDrinkRepository;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração contra um PostgreSQL real (embarcado, sem Docker). O banco é montado como em produção:
 * {@code db/script.sql} e, em seguida, as migrações do Flyway, com {@code ddl-auto=validate} conferindo as entidades.
 * Cada teste roda em uma transação desfeita ao final.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class PostgresIntegrationTestSupport {

    private static final EmbeddedPostgres POSTGRES = iniciarPostgres();

    @Autowired protected MockMvc mockMvc;
    @Autowired protected EntityManager entityManager;
    @Autowired protected ProfileRepository profileRepository;
    @Autowired protected FuncionalidadeRepository funcionalidadeRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected CategoriaRepository categoriaRepository;
    @Autowired protected ProdutoRepository produtoRepository;
    @Autowired protected EstoqueRepository estoqueRepository;
    @Autowired protected ComposicaoDrinkRepository composicaoDrinkRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    protected EstablishmentEntity estabelecimento;
    private final Map<String, String> tokensCsrf = new HashMap<>();

    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry registry) {
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

    @BeforeEach
    void prepararEstabelecimento() {
        estabelecimento = entityManager.merge(EstablishmentEntity.builder()
                .corporateName("Meraki LTDA")
                .tradeName("Meraki")
                .cnpj("12345678000199")
                .active(true)
                .build());
    }

    protected Cookie autenticarGerente(String email) throws Exception {
        return autenticar("Gerente", email, Set.of());
    }

    protected Cookie autenticarComPermissoes(String email, String... codigos) throws Exception {
        Map<String, FuncionalidadeEntity> existentes = funcionalidadeRepository.findByCodeIn(Arrays.asList(codigos))
                .stream().collect(Collectors.toMap(FuncionalidadeEntity::getCode, f -> f));
        Set<FuncionalidadeEntity> funcionalidades = Arrays.stream(codigos)
                .map(codigo -> existentes.computeIfAbsent(codigo, c -> funcionalidadeRepository.save(
                        FuncionalidadeEntity.builder().code(c).name(c).description("Permissão de teste").build())))
                .collect(Collectors.toSet());
        return autenticar("Operador " + UUID.randomUUID().toString().substring(0, 6), email, funcionalidades);
    }

    private Cookie autenticar(String nomePerfil, String email, Set<FuncionalidadeEntity> funcionalidades) throws Exception {
        ProfileEntity perfil = profileRepository.save(ProfileEntity.builder()
                .name(nomePerfil).establishment(estabelecimento).functionalities(funcionalidades).build());
        userRepository.save(UserEntity.builder()
                .name(nomePerfil + " Meraki").email(email)
                .passwordHash(passwordEncoder.encode("senha-segura"))
                .profile(perfil).establishment(estabelecimento).status(UserStatus.ATIVO).build());

        MvcResult login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"senha\":\"senha-segura\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn();
        Cookie accessToken = login.getResponse().getCookie("accessToken");
        MvcResult csrf = mockMvc.perform(get("/auth/csrf").cookie(accessToken))
                .andExpect(status().isOk()).andReturn();
        tokensCsrf.put(accessToken.getValue(), JsonPath.read(csrf.getResponse().getContentAsString(), "$.token"));
        return accessToken;
    }

    protected RequestPostProcessor csrf(Cookie accessToken) {
        return request -> {
            request.addHeader("X-XSRF-TOKEN", tokensCsrf.get(accessToken.getValue()));
            return request;
        };
    }

    protected CategoriaEntity categoria(String nome) {
        CategoriaEntity categoria = new CategoriaEntity();
        categoria.setEstabelecimentoId(estabelecimento.getId());
        categoria.setNome(nome);
        categoria.setAtivo(true);
        return categoriaRepository.saveAndFlush(categoria);
    }

    /** Produto padrão com custo 10 e venda 20. Fracionado: garrafa de 1000 ml e saldo em ML. */
    protected ProdutoEntity produtoPadrao(String nome, CategoriaEntity categoria, boolean fracionado, String saldo) {
        ProdutoEntity produto = new ProdutoEntity();
        produto.setEstabelecimentoId(estabelecimento.getId());
        produto.setCategoria(categoria);
        produto.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        produto.setNome(nome);
        produto.setTipo(ProdutoTipo.PADRAO);
        produto.setUnidadeMedida(fracionado ? "ml" : "un");
        produto.setPrecoCusto(new BigDecimal("10.00"));
        produto.setPrecoVenda(new BigDecimal("20.00"));
        produto.setFracionado(fracionado);
        produto.setVolumeEmbalagemMl(fracionado ? new BigDecimal("1000") : null);
        produto.setAtivo(true);
        produto = produtoRepository.saveAndFlush(produto);
        EstoqueEntity estoque = new EstoqueEntity();
        estoque.setProduto(produto);
        estoque.setQuantidadeAtual(new BigDecimal(saldo));
        estoque.setQuantidadeReservada(BigDecimal.ZERO);
        estoqueRepository.saveAndFlush(estoque);
        return produto;
    }

    /** Drink com venda 25 e a receita informada (ingrediente, quantidade, unidade). */
    protected ProdutoEntity drink(String nome, CategoriaEntity categoria, Object... receita) {
        ProdutoEntity drink = new ProdutoEntity();
        drink.setEstabelecimentoId(estabelecimento.getId());
        drink.setCategoria(categoria);
        drink.setSku("DRK-" + UUID.randomUUID().toString().substring(0, 8));
        drink.setNome(nome);
        drink.setTipo(ProdutoTipo.DRINK);
        drink.setUnidadeMedida("un");
        drink.setPrecoCusto(BigDecimal.ZERO);
        drink.setPrecoVenda(new BigDecimal("25.00"));
        drink.setFracionado(false);
        drink.setAtivo(true);
        drink = produtoRepository.saveAndFlush(drink);
        for (int i = 0; i < receita.length; i += 3) {
            ComposicaoDrinkEntity item = new ComposicaoDrinkEntity();
            item.setDrink(drink);
            item.setProduto((ProdutoEntity) receita[i]);
            item.setQuantidade(new BigDecimal(receita[i + 1].toString()));
            item.setUnidadeConsumo((UnidadeConsumoDrink) receita[i + 2]);
            composicaoDrinkRepository.saveAndFlush(item);
        }
        return drink;
    }

    protected BigDecimal saldo(ProdutoEntity produto) {
        entityManager.flush();
        entityManager.clear();
        return estoqueRepository.findByProdutoId(produto.getId()).orElseThrow().getQuantidadeAtual();
    }
}
