package valhalla.core.stock.app.modules.accesscontrol.integration;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
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
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.FuncionalidadeRepository;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccessControlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private FuncionalidadeRepository funcionalidadeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private EstablishmentEntity establishment;
    private ProfileEntity gerenteProfile;
    private ProfileEntity operadorProfile;
    private UserEntity gerenteUser;
    private UserEntity operadorUser;
    private final Map<String, String> tokensCsrfPorAccessToken = new HashMap<>();

    @BeforeEach
    void setUp() {
        establishment = entityManager.merge(EstablishmentEntity.builder()
                .corporateName("Meraki LTDA").tradeName("Meraki")
                .cnpj("12345678000199").build());

        gerenteProfile = profileRepository.save(ProfileEntity.builder()
                .name("Gerente").description("Acesso total").establishment(establishment).build());

        operadorProfile = profileRepository.save(ProfileEntity.builder()
                .name("Operador").description("Acesso de caixa").establishment(establishment).build());

        gerenteUser = userRepository.save(UserEntity.builder()
                .name("Gerente Meraki").email("gerente@meraki.com")
                .passwordHash(passwordEncoder.encode("senha-gerente"))
                .profile(gerenteProfile).establishment(establishment).status(UserStatus.ATIVO).build());

        operadorUser = userRepository.save(UserEntity.builder()
                .name("Operador Meraki").email("operador@meraki.com")
                .passwordHash(passwordEncoder.encode("senha-operador"))
                .profile(operadorProfile).establishment(establishment).status(UserStatus.ATIVO).build());

        // Garante que há ao menos uma funcionalidade no banco
        if (funcionalidadeRepository.findByCodeIn(java.util.List.of("PRODUTOS_CRIAR")).isEmpty()) {
            funcionalidadeRepository.save(FuncionalidadeEntity.builder()
                    .code("PRODUTOS_CRIAR").name("Criar produtos").description("Permite cadastrar produtos").build());
        }
    }

    @Test
    void deveListarFuncionalidadesDisponiveis() throws Exception {
        Cookie token = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(get("/autorizacoes/funcionalidades").cookie(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void deveListarPerfisDoEstabelecimento() throws Exception {
        Cookie token = login("gerente@meraki.com", "senha-gerente");

        mockMvc.perform(get("/autorizacoes/perfis").cookie(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.nome == 'Gerente')]").exists());
    }

    @Test
    void deveCriarConsultarAtualizarEExcluirPerfil() throws Exception {
        Cookie token = login("gerente@meraki.com", "senha-gerente");

        // 1. Criar perfil
        String requestCriar = """
                {
                  "nome": "Supervisor de Turno",
                  "descricao": "Coordena turno",
                  "codigosFuncionalidades": ["PRODUTOS_CRIAR"]
                }
                """;

        MvcResult createResult = mockMvc.perform(post("/autorizacoes/perfis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestCriar)
                        .cookie(token).with(csrf(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nome").value("Supervisor de Turno"))
                .andReturn();

        Integer profileId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

        // 2. Buscar perfil criado
        mockMvc.perform(get("/autorizacoes/perfis/{profileId}", profileId).cookie(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Supervisor de Turno"));

        // 3. Atualizar perfil
        String requestAtualizar = """
                {
                  "nome": "Supervisor Geral",
                  "descricao": "Coordena operação"
                }
                """;
        mockMvc.perform(patch("/autorizacoes/perfis/{profileId}", profileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestAtualizar)
                        .cookie(token).with(csrf(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Supervisor Geral"));

        // 4. Substituir funcionalidades
        mockMvc.perform(put("/autorizacoes/perfis/{profileId}/funcionalidades/codigos", profileId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"PRODUTOS_CRIAR\"]")
                        .cookie(token).with(csrf(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigosFuncionalidades[0]").value("PRODUTOS_CRIAR"));

        // 5. Excluir perfil sem usuários vinculados
        mockMvc.perform(delete("/autorizacoes/perfis/{profileId}", profileId)
                        .cookie(token).with(csrf(token)))
                .andExpect(status().isNoContent());

        assertThat(profileRepository.findById(profileId)).isEmpty();
    }

    @Test
    void deveGerenciarSobrescritasEBuscarPermissoesDoUsuario() throws Exception {
        Cookie token = login("gerente@meraki.com", "senha-gerente");

        // Substituir sobrescritas de permissão do operador
        String requestSobrescrita = """
                {
                  "sobrescritas": [
                    {
                      "codigoFuncionalidade": "PRODUTOS_CRIAR",
                      "efeito": "GRANT"
                    }
                  ]
                }
                """;

        mockMvc.perform(put("/autorizacoes/usuarios/{userId}/sobrescritas-permissao", operadorUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestSobrescrita)
                        .cookie(token).with(csrf(token)))
                .andExpect(status().isNoContent());

        // Consultar permissões do operador
        mockMvc.perform(get("/autorizacoes/usuarios/{userId}/permissoes", operadorUser.getId())
                        .cookie(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissoesEfetivas").isArray())
                .andExpect(jsonPath("$.sobrescritas[0].codigoFuncionalidade").value("PRODUTOS_CRIAR"))
                .andExpect(jsonPath("$.sobrescritas[0].efeito").value("GRANT"));
    }

    private Cookie login(String email, String password) throws Exception {
        MvcResult resultado = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
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
