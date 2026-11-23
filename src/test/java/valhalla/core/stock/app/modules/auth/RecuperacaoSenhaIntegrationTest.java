package valhalla.core.stock.app.modules.auth;

import jakarta.persistence.EntityManager;
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
import valhalla.core.stock.app.modules.auth.entity.PasswordResetTokenEntity;
import valhalla.core.stock.app.modules.auth.repository.PasswordResetTokenRepository;
import valhalla.core.stock.app.modules.auth.repository.UserSessionRepository;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecuperacaoSenhaIntegrationTest {

    private static final String SENHA_ATUAL = "senha-atual-123";
    private static final String NOVA_SENHA = "nova-senha-456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private EstablishmentEntity establishment;
    private ProfileEntity profile;
    private UserEntity usuario;

    @BeforeEach
    void setUp() {
        establishment = entityManager.merge(EstablishmentEntity.builder()
                .corporateName("Meraki LTDA").tradeName("Meraki")
                .cnpj("12345678000199").build());
        profile = profileRepository.save(ProfileEntity.builder()
                .name("Atendente")
                .description("Operação da adega")
                .establishment(establishment)
                .build());
        usuario = salvarUsuario("atendente@meraki.com", UserStatus.ATIVO);
    }

    @Test
    void deveResponderDaMesmaFormaQuandoEmailNaoEstaCadastrado() throws Exception {
        solicitarRecuperacao("inexistente@meraki.com")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.mensagem").value(
                        "Se o e-mail estiver cadastrado, você receberá as instruções para redefinir a senha"));

        assertThat(passwordResetTokenRepository.findAll()).isEmpty();
    }

    @Test
    void deveGerarTokenComHashEValidadeParaUsuarioAtivo() throws Exception {
        solicitarRecuperacao("ATENDENTE@Meraki.com").andExpect(status().isAccepted());

        List<PasswordResetTokenEntity> tokens = passwordResetTokenRepository.findAll();
        assertThat(tokens).hasSize(1);
        PasswordResetTokenEntity token = tokens.getFirst();
        assertThat(token.getUser().getId()).isEqualTo(usuario.getId());
        assertThat(token.getTokenHash()).hasSize(64).matches("[0-9a-f]+");
        assertThat(token.getUsedAt()).isNull();
        assertThat(Duration.between(token.getCreatedAt(), token.getExpiresAt()))
                .isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void deveLimitarSolicitacoesRepetidasEmCurtoIntervalo() throws Exception {
        solicitarRecuperacao(usuario.getEmail()).andExpect(status().isAccepted());
        solicitarRecuperacao(usuario.getEmail()).andExpect(status().isAccepted());

        assertThat(passwordResetTokenRepository.findAll()).hasSize(1);
    }

    @Test
    void naoDeveGerarTokenParaUsuarioInativo() throws Exception {
        salvarUsuario("inativo@meraki.com", UserStatus.INATIVO);

        solicitarRecuperacao("inativo@meraki.com").andExpect(status().isAccepted());

        assertThat(passwordResetTokenRepository.findAll()).isEmpty();
    }

    @Test
    void deveRejeitarEmailInvalidoNaSolicitacao() throws Exception {
        solicitarRecuperacao("nao-e-um-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void deveRedefinirSenhaEncerrarSessoesEInvalidarToken() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(SENHA_ATUAL)))
                .andExpect(status().isOk());
        assertThat(userSessionRepository.existsById(usuario.getId())).isTrue();

        salvarToken("token-valido", LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));

        redefinirSenha("token-valido", NOVA_SENHA)
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("accessToken", 0))
                .andExpect(cookie().maxAge("refreshToken", 0));

        entityManager.flush();
        entityManager.clear();
        assertThat(userSessionRepository.existsById(usuario.getId())).isFalse();
        assertThat(passwordEncoder.matches(NOVA_SENHA,
                userRepository.findById(usuario.getId()).orElseThrow().getPasswordHash())).isTrue();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(SENHA_ATUAL)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(NOVA_SENHA)))
                .andExpect(status().isOk());

        redefinirSenha("token-valido", "outra-senha-789")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token de recuperação inválido ou expirado"));
    }

    @Test
    void deveRejeitarTokenExpirado() throws Exception {
        salvarToken("token-expirado", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));

        redefinirSenha("token-expirado", NOVA_SENHA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token de recuperação inválido ou expirado"));
    }

    @Test
    void deveRejeitarTokenDesconhecido() throws Exception {
        redefinirSenha("token-desconhecido", NOVA_SENHA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Token de recuperação inválido ou expirado"));
    }

    @Test
    void deveRejeitarNovaSenhaCurta() throws Exception {
        salvarToken("token-valido", LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));

        redefinirSenha("token-valido", "curta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.novaSenha").exists());
    }

    @Test
    void novaSolicitacaoDeveInvalidarTokensPendentesAnteriores() throws Exception {
        PasswordResetTokenEntity antigo = salvarToken("token-antigo",
                LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        antigo.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        entityManager.flush();

        solicitarRecuperacao(usuario.getEmail()).andExpect(status().isAccepted());

        redefinirSenha("token-antigo", NOVA_SENHA).andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions solicitarRecuperacao(String email)
            throws Exception {
        return mockMvc.perform(post("/auth/recuperar-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s"}
                        """.formatted(email)));
    }

    private org.springframework.test.web.servlet.ResultActions redefinirSenha(String token, String senha)
            throws Exception {
        return mockMvc.perform(post("/auth/redefinir-senha")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token": "%s", "novaSenha": "%s"}
                        """.formatted(token, senha)));
    }

    private UserEntity salvarUsuario(String email, UserStatus status) {
        return userRepository.save(UserEntity.builder()
                .name("Atendente Meraki")
                .email(email)
                .passwordHash(passwordEncoder.encode(SENHA_ATUAL))
                .profile(profile)
                .establishment(establishment)
                .status(status)
                .build());
    }

    private PasswordResetTokenEntity salvarToken(String token, LocalDateTime expiraEm) throws Exception {
        PasswordResetTokenEntity entity = passwordResetTokenRepository.saveAndFlush(
                PasswordResetTokenEntity.builder()
                        .user(usuario)
                        .tokenHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(token.getBytes(StandardCharsets.UTF_8))))
                        .createdAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1))
                        .expiresAt(expiraEm)
                        .build());
        return entity;
    }

    private String loginJson(String senha) {
        return """
                {"email": "%s", "senha": "%s"}
                """.formatted(usuario.getEmail(), senha);
    }
}
