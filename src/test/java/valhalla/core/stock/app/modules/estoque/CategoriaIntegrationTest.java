package valhalla.core.stock.app.modules.estoque;

import jakarta.persistence.EntityManager;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.FuncionalidadeRepository;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.estoque.entity.CategoriaEntity;
import valhalla.core.stock.app.modules.estoque.repository.CategoriaRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoriaIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private FuncionalidadeRepository funcionalidadeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private EstablishmentEntity estabelecimento;
    private final Map<String, String> tokensCsrfPorAccessToken = new HashMap<>();

    @BeforeEach
    void prepararDados() {
        estabelecimento = entityManager.merge(EstablishmentEntity.builder()
                .corporateName("Meraki LTDA")
                .tradeName("Meraki")
                .cnpj("12345678000199")
                .build());
    }

    @Test
    void gerenteFiltraCategoriasDoProprioEstabelecimentoComPaginacaoNoBanco() throws Exception {
        criarCategoria(estabelecimento, "Bebidas", true);
        criarCategoria(estabelecimento, "Alimentos", true);
        criarCategoria(estabelecimento, "Inativa", false);

        EstablishmentEntity outroEstabelecimento = entityManager.merge(EstablishmentEntity.builder()
                .corporateName("Outra LTDA")
                .tradeName("Outra")
                .cnpj("98765432000199")
                .build());
        criarCategoria(outroEstabelecimento, "Categoria externa", true);

        Cookie tokenAcesso = autenticar("Gerente", "gerente@meraki.com");

        mockMvc.perform(get("/categorias")
                        .param("busca", "i")
                        .param("ativo", "true")
                        .param("pagina", "0")
                        .param("tamanho", "1")
                        .param("ordenarPor", "nome")
                        .param("direcao", "ASC")
                        .cookie(tokenAcesso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(2))
                .andExpect(jsonPath("$.totalPaginas").value(2))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(1))
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("Alimentos"))
                .andExpect(jsonPath("$.ultima").value(false));

        mockMvc.perform(get("/categorias").cookie(tokenAcesso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItens").value(3))
                .andExpect(jsonPath("$.itens.length()").value(3));
    }

    @Test
    void deveExigirSessaoParaListarCategorias() throws Exception {
        mockMvc.perform(get("/categorias"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejeitaParametrosDePaginacaoOuOrdenacaoInvalidos() throws Exception {
        Cookie tokenAcesso = autenticar("Gerente", "gerente@meraki.com");

        mockMvc.perform(get("/categorias").param("pagina", "-1").cookie(tokenAcesso))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.pagina").exists());

        mockMvc.perform(get("/categorias").param("ordenarPor", "ativo").cookie(tokenAcesso))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Campo de ordenação inválido: ativo"));
    }

    @Test
    void usuarioSemPermissaoDeVisualizarEstoqueNaoListaCategorias() throws Exception {
        Cookie tokenAcesso = autenticar("Atendente", "atendente@meraki.com");

        mockMvc.perform(get("/categorias").cookie(tokenAcesso))
                .andExpect(status().isForbidden());
    }

    @Test
    void usuarioComPermissaoDeVisualizarEstoqueListaCategorias() throws Exception {
        criarCategoria(estabelecimento, "Bebidas", true);
        Cookie tokenAcesso = autenticarComPermissao(
                "Operador de estoque",
                "operador@meraki.com",
                "VISUALIZAR_ESTOQUE"
        );

        mockMvc.perform(get("/categorias").cookie(tokenAcesso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(1))
                .andExpect(jsonPath("$.itens[0].nome").value("Bebidas"));
    }

    @Test
    void gerenteCriaCategoriaNoProprioEstabelecimento() throws Exception {
        Cookie tokenAcesso = autenticar("Gerente", "gerente@meraki.com");

        mockMvc.perform(post("/categorias")
                        .cookie(tokenAcesso)
                        .with(csrf(tokenAcesso))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"  Bebidas  ","descricao":" Produtos gelados "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Bebidas"))
                .andExpect(jsonPath("$.descricao").value("Produtos gelados"))
                .andExpect(jsonPath("$.ativo").value(true));
    }

    @Test
    void rejeitaAlteracaoDeEstadoSemTokenCsrf() throws Exception {
        Cookie tokenAcesso = autenticar("Gerente", "gerente@meraki.com");

        mockMvc.perform(post("/categorias")
                        .cookie(tokenAcesso)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Bebidas\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Token CSRF ausente ou inválido"));
    }

    @Test
    void naoPermiteCriarCategoriaComNomeDuplicadoSemDiferenciarMaiusculas() throws Exception {
        criarCategoria(estabelecimento, "Bebidas", true);
        Cookie tokenAcesso = autenticar("Gerente", "gerente@meraki.com");

        mockMvc.perform(post("/categorias")
                        .cookie(tokenAcesso)
                        .with(csrf(tokenAcesso))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"bebidas"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Categoria já existe no estabelecimento"));
    }

    @Test
    void gerenteBuscaEAtualizaCategoriaInclusiveDesativando() throws Exception {
        CategoriaEntity categoria = criarCategoria(estabelecimento, "Bebidas", true);
        categoria.setDescricao("Descrição antiga");
        categoriaRepository.saveAndFlush(categoria);
        Cookie tokenAcesso = autenticar("Gerente", "gerente@meraki.com");

        mockMvc.perform(get("/categorias/{id}", categoria.getId()).cookie(tokenAcesso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Bebidas"));

        mockMvc.perform(patch("/categorias/{id}", categoria.getId())
                        .cookie(tokenAcesso)
                        .with(csrf(tokenAcesso))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Bebidas geladas","descricao":"","ativo":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Bebidas geladas"))
                .andExpect(jsonPath("$.descricao").doesNotExist())
                .andExpect(jsonPath("$.ativo").value(false));

        mockMvc.perform(get("/categorias").param("ativo", "true").cookie(tokenAcesso))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens.length()").value(0));
    }

    @Test
    void gerenteExcluiCategoriaSemProdutosVinculados() throws Exception {
        CategoriaEntity categoria = criarCategoria(estabelecimento, "Bebidas", true);
        Cookie tokenAcesso = autenticar("Gerente", "gerente@meraki.com");

        mockMvc.perform(delete("/categorias/{id}", categoria.getId()).cookie(tokenAcesso).with(csrf(tokenAcesso)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/categorias/{id}", categoria.getId()).cookie(tokenAcesso))
                .andExpect(status().isNotFound());
    }

    @Test
    void gerenteNaoConsultaCategoriaDeOutroEstabelecimento() throws Exception {
        EstablishmentEntity outroEstabelecimento = entityManager.merge(EstablishmentEntity.builder()
                .corporateName("Outra LTDA")
                .tradeName("Outra")
                .cnpj("98765432000199")
                .build());
        CategoriaEntity categoriaExterna = criarCategoria(outroEstabelecimento, "Externa", true);
        Cookie tokenAcesso = autenticar("Gerente", "gerente@meraki.com");

        mockMvc.perform(get("/categorias/{id}", categoriaExterna.getId()).cookie(tokenAcesso))
                .andExpect(status().isForbidden());
    }

    private CategoriaEntity criarCategoria(EstablishmentEntity dono, String nome, boolean ativa) {
        CategoriaEntity categoria = new CategoriaEntity();
        categoria.setEstabelecimentoId(dono.getId());
        categoria.setNome(nome);
        categoria.setAtivo(ativa);
        return categoriaRepository.saveAndFlush(categoria);
    }

    private Cookie autenticar(String nomePerfil, String email) throws Exception {
        return autenticarComPermissoes(nomePerfil, email, Set.of());
    }

    private Cookie autenticarComPermissao(String nomePerfil, String email, String codigoPermissao) throws Exception {
        FuncionalidadeEntity funcionalidade = funcionalidadeRepository.save(FuncionalidadeEntity.builder()
                .code(codigoPermissao)
                .name(codigoPermissao)
                .description("Permissão de teste")
                .build());
        return autenticarComPermissoes(nomePerfil, email, Set.of(funcionalidade));
    }

    private Cookie autenticarComPermissoes(
            String nomePerfil,
            String email,
            Set<FuncionalidadeEntity> funcionalidades
    ) throws Exception {
        ProfileEntity perfil = profileRepository.save(ProfileEntity.builder()
                .name(nomePerfil)
                .establishment(estabelecimento)
                .functionalities(funcionalidades)
                .build());
        userRepository.save(UserEntity.builder()
                .name(nomePerfil + " Meraki")
                .email(email)
                .passwordHash(passwordEncoder.encode("senha-segura"))
                .profile(perfil)
                .establishment(estabelecimento)
                .status(UserStatus.ATIVO)
                .build());

        MvcResult resultado = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","senha":"senha-segura"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie accessToken = resultado.getResponse().getCookie("accessToken");
        MvcResult csrfResult = mockMvc.perform(get("/auth/csrf").cookie(accessToken))
                .andExpect(status().isOk())
                .andReturn();
        tokensCsrfPorAccessToken.put(accessToken.getValue(),
                JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token"));
        return accessToken;
    }

    private RequestPostProcessor csrf(Cookie accessToken) {
        return request -> {
            request.addHeader("X-XSRF-TOKEN", tokensCsrfPorAccessToken.get(accessToken.getValue()));
            return request;
        };
    }
}
