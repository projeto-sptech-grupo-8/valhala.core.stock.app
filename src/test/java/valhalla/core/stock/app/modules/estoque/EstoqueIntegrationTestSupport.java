package valhalla.core.stock.app.modules.estoque;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.FuncionalidadeRepository;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.entity.EstoqueEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.modules.estoque.repository.CategoriaRepository;
import valhalla.core.stock.app.modules.estoque.repository.EstoqueRepository;
import valhalla.core.stock.app.modules.estoque.repository.ProdutoRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class EstoqueIntegrationTestSupport {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected EntityManager entityManager;
    @Autowired protected ProfileRepository profileRepository;
    @Autowired protected FuncionalidadeRepository funcionalidadeRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected CategoriaRepository categoriaRepository;
    @Autowired protected ProdutoRepository produtoRepository;
    @Autowired protected EstoqueRepository estoqueRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    protected EstablishmentEntity estabelecimento;
    private final HashMap<String, String> tokensCsrf = new HashMap<>();

    @BeforeEach
    void prepararEstabelecimento() {
        estabelecimento = entityManager.merge(EstablishmentEntity.builder()
                .corporateName("Meraki LTDA")
                .tradeName("Meraki")
                .cnpj("12345678000199")
                .build());
    }

    protected Cookie autenticarGerente(String email) throws Exception {
        return autenticar("Gerente", email, Set.of());
    }

    protected Cookie autenticarComPermissoes(String email, String... codigos) throws Exception {
        Set<FuncionalidadeEntity> funcionalidades = java.util.Arrays.stream(codigos)
                .map(codigo -> funcionalidadeRepository.save(FuncionalidadeEntity.builder()
                        .code(codigo).name(codigo).description("Permissão de teste").build()))
                .collect(java.util.stream.Collectors.toSet());
        return autenticar("Operador", email, funcionalidades);
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

    protected ProdutoEntity produtoPadrao(String nome, CategoriaEntity categoria, boolean fracionado, String saldo) {
        ProdutoEntity produto = new ProdutoEntity();
        produto.setEstabelecimentoId(estabelecimento.getId());
        produto.setCategoria(categoria);
        produto.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        produto.setNome(nome);
        produto.setTipo(ProdutoTipo.PADRAO);
        produto.setUnidadeMedida(fracionado ? "ml" : "un");
        produto.setPrecoCusto(new BigDecimal("10"));
        produto.setPrecoVenda(new BigDecimal("20"));
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

    protected ProdutoEntity drink(String nome, CategoriaEntity categoria) {
        ProdutoEntity drink = new ProdutoEntity();
        drink.setEstabelecimentoId(estabelecimento.getId());
        drink.setCategoria(categoria);
        drink.setSku("DRK-" + UUID.randomUUID().toString().substring(0, 8));
        drink.setNome(nome);
        drink.setTipo(ProdutoTipo.DRINK);
        drink.setUnidadeMedida("un");
        drink.setPrecoCusto(BigDecimal.ZERO);
        drink.setPrecoVenda(new BigDecimal("25"));
        drink.setFracionado(false);
        drink.setAtivo(true);
        return produtoRepository.saveAndFlush(drink);
    }
}
