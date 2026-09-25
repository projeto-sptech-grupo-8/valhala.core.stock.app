package valhalla.core.stock.app.modules.auth;

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
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import jakarta.persistence.EntityManager;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.modules.auth.repository.UserSessionRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private EntityManager entityManager;

    private EstablishmentEntity establishment;
    private ProfileEntity gerenteProfile;
    private ProfileEntity atendenteProfile;
    private UserEntity gerenteUser;
    private UserEntity atendenteUser;

    @BeforeEach
    void setUp() {
        establishment = entityManager.merge(EstablishmentEntity.builder()
                .corporateName("Meraki LTDA").tradeName("Meraki")
                .cnpj("12345678000199").build());
        gerenteProfile = profileRepository.save(ProfileEntity.builder()
                .name("Gerente")
                .description("Pode gerenciar usuários")
                .establishment(establishment)
                .build());
        atendenteProfile = profileRepository.save(ProfileEntity.builder()
                .name("Atendente")
                .description("Operação da adega")
                .establishment(establishment)
                .build());

        gerenteUser = saveUser("gerente@meraki.com", "senha-gerente", gerenteProfile);
        atendenteUser = saveUser("atendente@meraki.com", "senha-atendente", atendenteProfile);
    }

    @Test
    void deveAutenticarUsuarioAtivoEEnviarTokensApenasNosCookies() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("gerente@meraki.com", "senha-gerente")))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("accessToken"))
                .andExpect(cookie().httpOnly("accessToken", true))
                .andExpect(cookie().path("accessToken", "/"))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().path("refreshToken", "/auth"))
                .andExpect(jsonPath("$.message")
                        .value("Autenticação realizada com sucesso"))
                .andExpect(jsonPath("$.user").value(gerenteUser.getName()))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());

        assertTrue(userSessionRepository.existsById(gerenteUser.getId()));
    }

    @Test
    void deveRenovarETrocarOsTokensPeloRefreshCookie() throws Exception {
        MvcResult loginResult = performLogin(
                "gerente@meraki.com",
                "senha-gerente"
        );
        Cookie oldAccessToken = requireCookie(loginResult, "accessToken");
        Cookie refreshToken = requireCookie(loginResult, "refreshToken");

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .cookie(refreshToken))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("accessToken"))
                .andExpect(cookie().httpOnly("accessToken", true))
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(jsonPath("$.message")
                        .value("Autenticação renovada com sucesso"))
                .andExpect(jsonPath("$.user").value(gerenteUser.getName()))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        Cookie newRefreshToken = requireCookie(refreshResult, "refreshToken");
        Cookie newAccessToken = requireCookie(refreshResult, "accessToken");
        assertNotEquals(refreshToken.getValue(), newRefreshToken.getValue());

        mockMvc.perform(post("/usuario")
                        .cookie(oldAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("access-antigo@meraki.com")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh").cookie(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token inválido"));

        mockMvc.perform(post("/usuario")
                        .cookie(newAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("access-novo@meraki.com")))
                .andExpect(status().isCreated());
    }

    @Test
    void naoDeveRenovarSemRefreshCookie() throws Exception {
        mockMvc.perform(post("/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Refresh token não informado"));
    }

    @Test
    void deveRevogarSessaoELimparCookiesNoLogout() throws Exception {
        MvcResult loginResult = performLogin(
                "gerente@meraki.com",
                "senha-gerente"
        );
        Cookie accessToken = requireCookie(loginResult, "accessToken");
        Cookie refreshToken = requireCookie(loginResult, "refreshToken");

        mockMvc.perform(post("/auth/logout")
                        .cookie(accessToken, refreshToken))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value("accessToken", ""))
                .andExpect(cookie().maxAge("accessToken", 0))
                .andExpect(cookie().value("refreshToken", ""))
                .andExpect(cookie().maxAge("refreshToken", 0));

        mockMvc.perform(post("/usuario")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("apos-logout@meraki.com")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh").cookie(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token inválido"));
    }

    @Test
    void logoutDeveSerIdempotenteSemCookies() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("accessToken", 0))
                .andExpect(cookie().maxAge("refreshToken", 0));
    }

    @Test
    void logoutDeveRevogarSessaoMesmoSemRefreshCookie() throws Exception {
        MvcResult loginResult = performLogin(
                "gerente@meraki.com",
                "senha-gerente"
        );
        Cookie accessToken = requireCookie(loginResult, "accessToken");
        Cookie refreshToken = requireCookie(loginResult, "refreshToken");

        mockMvc.perform(post("/auth/logout").cookie(accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh").cookie(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token inválido"));
    }

    @Test
    void novoLoginDeveInvalidarSessaoAnterior() throws Exception {
        MvcResult firstLogin = performLogin(
                "gerente@meraki.com",
                "senha-gerente"
        );
        MvcResult secondLogin = performLogin(
                "gerente@meraki.com",
                "senha-gerente"
        );
        Cookie firstAccess = requireCookie(firstLogin, "accessToken");
        Cookie secondAccess = requireCookie(secondLogin, "accessToken");

        mockMvc.perform(post("/usuario")
                        .cookie(firstAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("primeira-sessao@meraki.com")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/usuario")
                        .cookie(secondAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("segunda-sessao@meraki.com")))
                .andExpect(status().isCreated());
    }

    @Test
    void naoDeveAceitarAccessTokenComoRefreshToken() throws Exception {
        Cookie accessToken = login("gerente@meraki.com", "senha-gerente");
        Cookie invalidRefreshCookie = new Cookie(
                "refreshToken",
                accessToken.getValue()
        );

        mockMvc.perform(post("/auth/refresh").cookie(invalidRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token inválido"));
    }

    @Test
    void naoDeveAceitarRefreshTokenComoAccessToken() throws Exception {
        MvcResult loginResult = performLogin(
                "gerente@meraki.com",
                "senha-gerente"
        );
        Cookie refreshToken = requireCookie(loginResult, "refreshToken");
        Cookie invalidAccessCookie = new Cookie(
                "accessToken",
                refreshToken.getValue()
        );

        mockMvc.perform(post("/usuario")
                        .cookie(invalidAccessCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("token-invalido@meraki.com")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Token inválido ou expirado"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void naoDeveAutenticarComSenhaInvalida() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("gerente@meraki.com", "senha-incorreta")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }

    @Test
    void naoDeveAutenticarUsuarioInativo() throws Exception {
        UserEntity inactiveUser = userRepository.save(UserEntity.builder()
                .name("Usuário Inativo")
                .email("inativo@meraki.com")
                .passwordHash(passwordEncoder.encode("senha-inativo"))
                .profile(atendenteProfile)
                .establishment(establishment)
                .status(UserStatus.INATIVO)
                .build());
        userRepository.flush();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(inactiveUser.getEmail(), "senha-inativo")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"));
    }

    @Test
    void naoDeveCadastrarUsuarioSemToken() throws Exception {
        mockMvc.perform(post("/usuario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("sem-token@meraki.com")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Token inválido ou expirado"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void devePermitirPreflightDoFrontendConfigurado() throws Exception {
        mockMvc.perform(options("/usuario")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:5173"
                ))
                .andExpect(header().string(
                        "Access-Control-Allow-Credentials",
                        "true"
                ));
    }

    @Test
    void atendenteNaoDeveCadastrarUsuario() throws Exception {
        Cookie accessToken = login("atendente@meraki.com", "senha-atendente");

        mockMvc.perform(post("/usuario")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("novo@meraki.com")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Acesso negado"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void permissaoDoPerfilDeveAutorizarCadastroDeUsuario() throws Exception {
        FuncionalidadeEntity functionality = entityManager.merge(
                FuncionalidadeEntity.builder().code("GERENCIAR_USUARIOS")
                        .name("Gerenciar usuários").build());
        atendenteProfile.getFunctionalities().add(functionality);
        profileRepository.saveAndFlush(atendenteProfile);
        Cookie accessToken = login("atendente@meraki.com", "senha-atendente");

        mockMvc.perform(post("/usuario").cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("permissao-perfil@meraki.com")))
                .andExpect(status().isCreated());
    }

    @Test
    void permissaoDiretaDoUsuarioDeveAutorizarCadastroDeUsuario() throws Exception {
        FuncionalidadeEntity functionality = entityManager.merge(
                FuncionalidadeEntity.builder().code("GERENCIAR_USUARIOS")
                        .name("Gerenciar usuários").build());
        atendenteUser.getDirectFunctionalities().add(functionality);
        userRepository.saveAndFlush(atendenteUser);
        Cookie accessToken = login("atendente@meraki.com", "senha-atendente");

        mockMvc.perform(post("/usuario").cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("permissao-direta@meraki.com")))
                .andExpect(status().isCreated());
    }

    @Test
    void deveRetornarMensagemPadronizadaParaSenhaCurtaNoCadastro() throws Exception {
        Cookie accessToken = login("gerente@meraki.com", "senha-gerente");
        String request = """
                {
                  "name": "Novo Usuário",
                  "email": "senha-curta@meraki.com",
                  "password": "curta",
                  "profileName": "Atendente"
                }
                """;

        mockMvc.perform(post("/usuario")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Dados de entrada inválidos"))
                .andExpect(jsonPath("$.fieldErrors.password")
                        .value("A senha deve ter entre 8 e 72 caracteres"));
    }

    @Test
    void gerenteDeveCadastrarUsuario() throws Exception {
        Cookie accessToken = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(post("/usuario")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("novo@meraki.com")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/usuario/")))
                .andExpect(jsonPath("$.email").value("novo@meraki.com"))
                .andExpect(jsonPath("$.profileName").value("Atendente"));
    }

    @Test
    void deveRetornarConflitoAoCadastrarEmailExistente() throws Exception {
        Cookie accessToken = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(post("/usuario")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("atendente@meraki.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email informado já cadastrado"));
    }

    @Test
    void deveRetornarPerfilNaoEncontrado() throws Exception {
        Cookie accessToken = login("gerente@meraki.com", "senha-gerente");
        String request = """
                {
                  "name": "Novo Usuário",
                  "email": "perfil-inexistente@meraki.com",
                  "password": "senha-novo-usuario",
                  "profileName": "Perfil inexistente"
                }
                """;

        mockMvc.perform(post("/usuario")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Perfil não encontrado: Perfil inexistente"));
    }

    @Test
    void gerenteDeveListarUsuariosOrdenadosPorNome() throws Exception {
        Cookie accessToken = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(get("/usuario").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id")
                        .value(atendenteUser.getId().toString()))
                .andExpect(jsonPath("$[0].email")
                        .value("atendente@meraki.com"))
                .andExpect(jsonPath("$[1].id")
                        .value(gerenteUser.getId().toString()))
                .andExpect(jsonPath("$[1].email")
                        .value("gerente@meraki.com"));
    }

    @Test
    void gerenteDeveBuscarUsuarioPorId() throws Exception {
        Cookie accessToken = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(get("/usuario/{id}", atendenteUser.getId())
                        .cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(atendenteUser.getId().toString()))
                .andExpect(jsonPath("$.email").value("atendente@meraki.com"))
                .andExpect(jsonPath("$.profileName").value("Atendente"));
    }

    @Test
    void gerenteNaoDeveConsultarUsuarioDeOutroEstabelecimento() throws Exception {
        EstablishmentEntity otherEstablishment = entityManager.merge(
                EstablishmentEntity.builder().corporateName("Outra LTDA")
                        .tradeName("Outra").cnpj("98765432000199").build());
        ProfileEntity otherProfile = profileRepository.save(ProfileEntity.builder()
                .name("Gerente").establishment(otherEstablishment).build());
        UserEntity otherUser = userRepository.save(UserEntity.builder()
                .name("Gerente Externo").email("externo@meraki.com")
                .passwordHash(passwordEncoder.encode("senha-externo"))
                .establishment(otherEstablishment).profile(otherProfile)
                .status(UserStatus.ATIVO).build());
        Cookie accessToken = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(get("/usuario/{id}", otherUser.getId()).cookie(accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void atendenteNaoDeveListarNemConsultarUsuariosPelaRotaAdministrativa()
            throws Exception {
        Cookie accessToken = login("atendente@meraki.com", "senha-atendente");

        mockMvc.perform(get("/usuario").cookie(accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Acesso negado"));

        mockMvc.perform(get("/usuario/{id}", atendenteUser.getId())
                        .cookie(accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Acesso negado"));
    }

    @Test
    void gerenteDeveReceberNotFoundAoConsultarUsuarioInexistente()
            throws Exception {
        Cookie accessToken = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(get("/usuario/{id}", UUID.randomUUID())
                        .cookie(accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado"));
    }

    @Test
    void deveRetornarUsuarioAtualPeloToken() throws Exception {
        Cookie accessToken = login("atendente@meraki.com", "senha-atendente");

        mockMvc.perform(get("/usuario/me").cookie(accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(atendenteUser.getId().toString()))
                .andExpect(jsonPath("$.email").value("atendente@meraki.com"))
                .andExpect(jsonPath("$.profileName").value("Atendente"));
    }

    @Test
    void atendenteDeveAtualizarAPropriaConta() throws Exception {
        MvcResult loginResult = performLogin(
                "atendente@meraki.com",
                "senha-atendente"
        );
        Cookie accessToken = requireCookie(loginResult, "accessToken");
        Cookie refreshToken = requireCookie(loginResult, "refreshToken");
        String request = """
                {
                  "name": "Atendente Atualizado",
                  "email": "atendente.novo@meraki.com",
                  "phone": "11999999999"
                }
                """;

        mockMvc.perform(patch("/usuario/me")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Atendente Atualizado"))
                .andExpect(jsonPath("$.email").value("atendente.novo@meraki.com"))
                .andExpect(jsonPath("$.phone").value("11999999999"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        // O token continua identificando o proprietário pelo userId mesmo após trocar o e-mail.
        mockMvc.perform(patch("/usuario/me")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"11888888888\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("11888888888"));

        MvcResult secondLogin = performLogin(
                "atendente.novo@meraki.com",
                "senha-atendente"
        );
        Cookie secondAccessToken = requireCookie(secondLogin, "accessToken");
        Cookie secondRefreshToken = requireCookie(secondLogin, "refreshToken");

        mockMvc.perform(patch("/usuario/me")
                        .cookie(secondAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"nova-senha-atendente\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/usuario/me")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"11777777777\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh").cookie(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token inválido"));

        mockMvc.perform(patch("/usuario/me")
                        .cookie(secondAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"11666666666\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh").cookie(secondRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token inválido"));

        login("atendente.novo@meraki.com", "nova-senha-atendente");
    }

    @Test
    void atendenteNaoDeveAtualizarContaDeOutroUsuario() throws Exception {
        Cookie accessToken = login("atendente@meraki.com", "senha-atendente");

        mockMvc.perform(patch("/usuario/{id}", gerenteUser.getId())
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alteração indevida\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void atendenteNaoDeveAlterarOProprioPerfilOuStatus() throws Exception {
        Cookie accessToken = login("atendente@meraki.com", "senha-atendente");

        mockMvc.perform(patch("/usuario/me")
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"Gerente\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Acesso negado"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void gerenteDeveAlterarPerfilEStatusDeOutroUsuario() throws Exception {
        Cookie accessToken = login("gerente@meraki.com", "senha-gerente");
        String request = """
                {
                  "profileName": "Gerente",
                  "active": false
                }
                """;

        mockMvc.perform(patch("/usuario/{id}", atendenteUser.getId())
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileName").value("Gerente"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deveInvalidarTokensQuandoUsuarioForDesativado() throws Exception {
        MvcResult atendenteLogin = performLogin(
                "atendente@meraki.com",
                "senha-atendente"
        );
        Cookie atendenteAccess = requireCookie(atendenteLogin, "accessToken");
        Cookie atendenteRefresh = requireCookie(atendenteLogin, "refreshToken");
        Cookie gerenteAccess = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(patch("/usuario/{id}", atendenteUser.getId())
                        .cookie(gerenteAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(patch("/usuario/{id}", atendenteUser.getId())
                        .cookie(atendenteAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"11911111111\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh").cookie(atendenteRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token inválido"));
    }

    @Test
    void deveInvalidarTokensQuandoUsuarioPerderPermissao() throws Exception {
        MvcResult gerenteLogin = performLogin(
                "gerente@meraki.com",
                "senha-gerente"
        );
        Cookie oldAccessToken = requireCookie(gerenteLogin, "accessToken");
        Cookie oldRefreshToken = requireCookie(gerenteLogin, "refreshToken");

        mockMvc.perform(patch("/usuario/{id}", gerenteUser.getId())
                        .cookie(oldAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"Atendente\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileName").value("Atendente"));

        mockMvc.perform(post("/usuario")
                        .cookie(oldAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("permissao-antiga@meraki.com")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh").cookie(oldRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token inválido"));

        Cookie newAccessToken = login("gerente@meraki.com", "senha-gerente");
        mockMvc.perform(post("/usuario")
                        .cookie(newAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("sem-permissao@meraki.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerenteDeveReceberNotFoundParaUsuarioInexistente() throws Exception {
        Cookie accessToken = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(patch("/usuario/{id}", UUID.randomUUID())
                        .cookie(accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Usuário inexistente\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado"));
    }

    @Test
    void gerenteDeveExcluirOutroUsuario() throws Exception {
        Cookie accessToken = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(delete("/usuario/{id}", atendenteUser.getId())
                        .cookie(accessToken))
                .andExpect(status().isNoContent());

        assertFalse(userRepository.existsById(atendenteUser.getId()));
    }

    @Test
    void atendenteDeveExcluirPropriaConta() throws Exception {
        MvcResult loginResult = performLogin(
                "atendente@meraki.com",
                "senha-atendente"
        );
        Cookie accessToken = requireCookie(loginResult, "accessToken");
        Cookie refreshToken = requireCookie(loginResult, "refreshToken");

        mockMvc.perform(delete("/usuario/me")
                        .cookie(accessToken))
                .andExpect(status().isNoContent());

        assertFalse(userRepository.existsById(atendenteUser.getId()));

        mockMvc.perform(get("/usuario/me").cookie(accessToken))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/auth/refresh").cookie(refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Refresh token inválido"));
    }

    @Test
    void atendenteNaoDeveExcluirOutroUsuario() throws Exception {
        Cookie accessToken = login("atendente@meraki.com", "senha-atendente");

        mockMvc.perform(delete("/usuario/{id}", gerenteUser.getId())
                        .cookie(accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerenteDeveReceberNotFoundAoExcluirUsuarioInexistente() throws Exception {
        Cookie accessToken = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(delete("/usuario/{id}", UUID.randomUUID())
                        .cookie(accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado"));
    }

    private UserEntity saveUser(String email, String password, ProfileEntity profile) {
        return userRepository.save(UserEntity.builder()
                .name(profile.getName() + " Meraki")
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .profile(profile)
                .establishment(establishment)
                .status(UserStatus.ATIVO)
                .build());
    }

    private Cookie login(String email, String password) throws Exception {
        return requireCookie(performLogin(email, password), "accessToken");
    }

    private MvcResult performLogin(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private Cookie requireCookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertNotNull(cookie, "Cookie " + name + " não encontrado na resposta");
        return cookie;
    }

    private String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private String createUserJson(String email) {
        return """
                {
                  "name": "Novo Usuário",
                  "email": "%s",
                  "phone": "11977777777",
                  "password": "senha-novo-usuario",
                  "profileName": "atendente"
                }
                """.formatted(email);
    }
}
