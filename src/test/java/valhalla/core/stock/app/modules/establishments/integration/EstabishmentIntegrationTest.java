package valhalla.core.stock.app.modules.establishments.integration;

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
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EstabishmentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private EntityManager entityManager;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private EstablishmentEntity establishment;
    private EstablishmentEntity outroEstabelecimento;
    private final Map<String, String> tokensCsrfPorAccessToken = new HashMap<>();

    @BeforeEach
    void setUp() {
        establishment = entityManager.merge(EstablishmentEntity.builder()
                .corporateName("Adega Meraki LTDA")
                .tradeName("Adega Meraki")
                .cnpj("12345678000199")
                .contactEmail("contato@meraki.com.br")
                .phone("1140000000")
                .address("R. das Bebidas, 420")
                .build());

        outroEstabelecimento = entityManager.merge(EstablishmentEntity.builder()
                .corporateName("Outra Adega LTDA")
                .tradeName("Outra Adega")
                .cnpj("98765432000110")
                .build());

        ProfileEntity gerenteProfile = profileRepository.save(ProfileEntity.builder()
                .name("Gerente")
                .description("Acesso total")
                .establishment(establishment)
                .build());

        ProfileEntity operadorProfile = profileRepository.save(ProfileEntity.builder()
                .name("Operador")
                .description("Operação padrão")
                .establishment(establishment)
                .build());

        userRepository.save(UserEntity.builder()
                .name("Gerente Meraki")
                .email("gerente.estab@meraki.com")
                .passwordHash(passwordEncoder.encode("senha-gerente"))
                .profile(gerenteProfile)
                .establishment(establishment)
                .status(UserStatus.ATIVO)
                .build());

        userRepository.save(UserEntity.builder()
                .name("Operador Meraki")
                .email("operador.estab@meraki.com")
                .passwordHash(passwordEncoder.encode("senha-operador"))
                .profile(operadorProfile)
                .establishment(establishment)
                .status(UserStatus.ATIVO)
                .build());
    }

    @Test
    void deveConsultarEstabelecimentoAutenticadoComoGerenteEOperadorEBloquearSemSessao() throws Exception {
        mockMvc.perform(get("/estabelecimento"))
                .andExpect(status().isUnauthorized());

        Cookie tokenGerente = login("gerente.estab@meraki.com", "senha-gerente");
        mockMvc.perform(get("/estabelecimento").cookie(tokenGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(establishment.getId().toString()))
                .andExpect(jsonPath("$.nome").value("Adega Meraki"))
                .andExpect(jsonPath("$.cnpj").value("12345678000199"))
                .andExpect(jsonPath("$.emailContato").value("contato@meraki.com.br"))
                .andExpect(jsonPath("$.telefone").value("1140000000"))
                .andExpect(jsonPath("$.endereco").value("R. das Bebidas, 420"))
                .andExpect(jsonPath("$.ativo").value(true));

        Cookie tokenOperador = login("operador.estab@meraki.com", "senha-operador");
        mockMvc.perform(get("/estabelecimento").cookie(tokenOperador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(establishment.getId().toString()))
                .andExpect(jsonPath("$.nome").value("Adega Meraki"));
    }

    @Test
    void deveAtualizarEstabelecimentoComPatchEPutComoGerente() throws Exception {
        Cookie tokenGerente = login("gerente.estab@meraki.com", "senha-gerente");

        mockMvc.perform(patch("/estabelecimento")
                        .cookie(tokenGerente)
                        .with(csrf(tokenGerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Adega Meraki Boutique",
                                  "cnpj": "11.222.333/0001-44",
                                  "emailContato": "Novo@Meraki.com.br",
                                  "telefone": "(11) 99999-8888",
                                  "endereco": "Av. Paulista, 1000"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Adega Meraki Boutique"))
                .andExpect(jsonPath("$.cnpj").value("11222333000144"))
                .andExpect(jsonPath("$.emailContato").value("novo@meraki.com.br"))
                .andExpect(jsonPath("$.telefone").value("(11) 99999-8888"))
                .andExpect(jsonPath("$.endereco").value("Av. Paulista, 1000"));

        mockMvc.perform(put("/estabelecimento")
                        .cookie(tokenGerente)
                        .with(csrf(tokenGerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "Adega Meraki Central",
                                  "cnpj": "11222333000144",
                                  "emailContato": "central@meraki.com.br",
                                  "telefone": "",
                                  "endereco": "Rua Vergueiro, 200"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Adega Meraki Central"))
                .andExpect(jsonPath("$.emailContato").value("central@meraki.com.br"))
                .andExpect(jsonPath("$.telefone").isEmpty())
                .andExpect(jsonPath("$.endereco").value("Rua Vergueiro, 200"));

        mockMvc.perform(get("/estabelecimento").cookie(tokenGerente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Adega Meraki Central"))
                .andExpect(jsonPath("$.cnpj").value("11222333000144"))
                .andExpect(jsonPath("$.emailContato").value("central@meraki.com.br"))
                .andExpect(jsonPath("$.endereco").value("Rua Vergueiro, 200"));
    }

    @Test
    void deveBloquearAtualizacaoQuandoNaoForGerente() throws Exception {
        Cookie tokenOperador = login("operador.estab@meraki.com", "senha-operador");

        mockMvc.perform(patch("/estabelecimento")
                        .cookie(tokenOperador)
                        .with(csrf(tokenOperador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Tentativa Operador\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/estabelecimento")
                        .cookie(tokenOperador)
                        .with(csrf(tokenOperador))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Tentativa Operador\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveValidarDadosERejeitarCnpjDuplicado() throws Exception {
        Cookie tokenGerente = login("gerente.estab@meraki.com", "senha-gerente");

        mockMvc.perform(patch("/estabelecimento")
                        .cookie(tokenGerente)
                        .with(csrf(tokenGerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/estabelecimento")
                        .cookie(tokenGerente)
                        .with(csrf(tokenGerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"123\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/estabelecimento")
                        .cookie(tokenGerente)
                        .with(csrf(tokenGerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailContato\":\"email-invalido\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/estabelecimento")
                        .cookie(tokenGerente)
                        .with(csrf(tokenGerente))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cnpj\":\"" + outroEstabelecimento.getCnpj() + "\"}"))
                .andExpect(status().isConflict());
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
        tokensCsrfPorAccessToken.put(
                accessToken.getValue(),
                JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token")
        );
        return accessToken;
    }

    private RequestPostProcessor csrf(Cookie accessToken) {
        return request -> {
            request.addHeader("X-XSRF-TOKEN", tokensCsrfPorAccessToken.get(accessToken.getValue()));
            return request;
        };
    }
}
