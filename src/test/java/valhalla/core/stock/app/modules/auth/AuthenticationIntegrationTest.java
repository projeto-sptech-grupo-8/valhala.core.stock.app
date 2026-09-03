package valhalla.core.stock.app.modules.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationIntegrationTest {

    private static final Pattern ACCESS_TOKEN_PATTERN =
            Pattern.compile("\\\"accessToken\\\":\\\"([^\\\"]+)\\\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private ProfileEntity gerenteProfile;
    private ProfileEntity atendenteProfile;
    private UserEntity gerenteUser;
    private UserEntity atendenteUser;

    @BeforeEach
    void setUp() {
        gerenteProfile = profileRepository.save(ProfileEntity.builder()
                .name("Gerente")
                .description("Pode gerenciar usuários")
                .build());
        atendenteProfile = profileRepository.save(ProfileEntity.builder()
                .name("Atendente")
                .description("Operação da adega")
                .build());

        gerenteUser = saveUser("gerente@meraki.com", "senha-gerente", gerenteProfile);
        atendenteUser = saveUser("atendente@meraki.com", "senha-atendente", atendenteProfile);
    }

    @Test
    void deveAutenticarUsuarioAtivoEEmitirJwt() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("gerente@meraki.com", "senha-gerente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));
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
                .active(false)
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
                .andExpect(status().isUnauthorized());
    }

    @Test
    void devePermitirPreflightDoFrontendConfigurado() throws Exception {
        mockMvc.perform(options("/usuario")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:5173"
                ));
    }

    @Test
    void atendenteNaoDeveCadastrarUsuario() throws Exception {
        String token = login("atendente@meraki.com", "senha-atendente");

        mockMvc.perform(post("/usuario")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("novo@meraki.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerenteDeveCadastrarUsuario() throws Exception {
        String token = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(post("/usuario")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("novo@meraki.com")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/usuario/")))
                .andExpect(jsonPath("$.email").value("novo@meraki.com"))
                .andExpect(jsonPath("$.profileName").value("Atendente"));
    }

    @Test
    void deveRetornarConflitoAoCadastrarEmailExistente() throws Exception {
        String token = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(post("/usuario")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createUserJson("atendente@meraki.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email informado já cadastrado"));
    }

    @Test
    void deveRetornarPerfilNaoEncontrado() throws Exception {
        String token = login("gerente@meraki.com", "senha-gerente");
        String request = """
                {
                  "name": "Novo Usuário",
                  "email": "perfil-inexistente@meraki.com",
                  "password": "senha-novo-usuario",
                  "profileName": "Perfil inexistente"
                }
                """;

        mockMvc.perform(post("/usuario")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Perfil não encontrado: Perfil inexistente"));
    }

    @Test
    void atendenteDeveAtualizarAPropriaConta() throws Exception {
        String token = login("atendente@meraki.com", "senha-atendente");
        String request = """
                {
                  "name": "Atendente Atualizado",
                  "email": "atendente.novo@meraki.com",
                  "phone": "11999999999",
                  "password": "nova-senha-atendente"
                }
                """;

        mockMvc.perform(patch("/usuario/{id}", atendenteUser.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Atendente Atualizado"))
                .andExpect(jsonPath("$.email").value("atendente.novo@meraki.com"))
                .andExpect(jsonPath("$.phone").value("11999999999"))
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());

        // O token continua identificando o proprietário pelo userId mesmo após trocar o e-mail.
        mockMvc.perform(patch("/usuario/{id}", atendenteUser.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"11888888888\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("11888888888"));

        login("atendente.novo@meraki.com", "nova-senha-atendente");
    }

    @Test
    void atendenteNaoDeveAtualizarContaDeOutroUsuario() throws Exception {
        String token = login("atendente@meraki.com", "senha-atendente");

        mockMvc.perform(patch("/usuario/{id}", gerenteUser.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alteração indevida\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void atendenteNaoDeveAlterarOProprioPerfilOuStatus() throws Exception {
        String token = login("atendente@meraki.com", "senha-atendente");

        mockMvc.perform(patch("/usuario/{id}", atendenteUser.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileName\":\"Gerente\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void gerenteDeveAlterarPerfilEStatusDeOutroUsuario() throws Exception {
        String token = login("gerente@meraki.com", "senha-gerente");
        String request = """
                {
                  "profileName": "Gerente",
                  "active": false
                }
                """;

        mockMvc.perform(patch("/usuario/{id}", atendenteUser.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileName").value("Gerente"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void gerenteDeveReceberNotFoundParaUsuarioInexistente() throws Exception {
        String token = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(patch("/usuario/{id}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Usuário inexistente\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuário não encontrado"));
    }

    private UserEntity saveUser(String email, String password, ProfileEntity profile) {
        return userRepository.save(UserEntity.builder()
                .name(profile.getName() + " Meraki")
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .profile(profile)
                .active(true)
                .build());
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Matcher matcher = ACCESS_TOKEN_PATTERN.matcher(response);
        if (!matcher.find()) {
            throw new AssertionError("Token JWT não encontrado na resposta de login");
        }
        return matcher.group(1);
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
