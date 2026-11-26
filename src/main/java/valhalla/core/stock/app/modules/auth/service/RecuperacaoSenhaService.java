package valhalla.core.stock.app.modules.auth.service;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.UriComponentsBuilder;
import valhalla.core.stock.app.modules.auth.config.RecuperacaoSenhaProperties;
import valhalla.core.stock.app.modules.auth.entity.PasswordResetTokenEntity;
import valhalla.core.stock.app.modules.auth.repository.PasswordResetTokenRepository;
import valhalla.core.stock.app.modules.auth.security.CsrfTokenStore;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.modules.users.service.UserAccessHistoryService;
import valhalla.core.stock.app.shared.error.InvalidPasswordResetTokenException;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Recuperação de senha em duas etapas:
 * <ol>
 *     <li>{@link #solicitar(String)} gera um token de uso único e o envia por e-mail;</li>
 *     <li>{@link #redefinir(String, String)} valida o token, troca a senha e encerra as sessões.</li>
 * </ol>
 * A solicitação nunca revela se o e-mail está cadastrado. Somente o hash do token é persistido.
 */
@Service
public class RecuperacaoSenhaService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecuperacaoSenhaService.class);
    private static final String MENSAGEM_TOKEN_INVALIDO = "Token de recuperação inválido ou expirado";
    private static final int BYTES_TOKEN = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenStateService tokenStateService;
    private final EmailRecuperacaoSenha emailRecuperacaoSenha;
    private final RecuperacaoSenhaProperties properties;
    private final BusinessEventLogger businessEventLogger;
    private final UserAccessHistoryService userAccessHistoryService;
    private final CsrfTokenStore csrfTokenStore;

    public RecuperacaoSenhaService(
            UserRepository userRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            PasswordEncoder passwordEncoder,
            TokenStateService tokenStateService,
            EmailRecuperacaoSenha emailRecuperacaoSenha,
            RecuperacaoSenhaProperties properties,
            BusinessEventLogger businessEventLogger,
            UserAccessHistoryService userAccessHistoryService,
            CsrfTokenStore csrfTokenStore
    ) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenStateService = tokenStateService;
        this.emailRecuperacaoSenha = emailRecuperacaoSenha;
        this.properties = properties;
        this.businessEventLogger = businessEventLogger;
        this.userAccessHistoryService = userAccessHistoryService;
        this.csrfTokenStore = csrfTokenStore;
    }

    @Transactional
    public void solicitar(String email) {
        String emailNormalizado = email.trim().toLowerCase(Locale.ROOT);
        Optional<UserEntity> usuarioEncontrado = userRepository.findByEmailIgnoreCase(emailNormalizado)
                .filter(UserEntity::isActive);
        if (usuarioEncontrado.isEmpty()) {
            LOGGER.atInfo().addKeyValue("event", "auth.password-reset.ignored")
                    .log("Password reset requested for unknown or inactive account");
            return;
        }

        UserEntity usuario = usuarioEncontrado.get();
        LocalDateTime agora = agora();
        if (passwordResetTokenRepository.existsByUser_IdAndCreatedAtAfter(
                usuario.getId(), agora.minus(properties.intervaloMinimoEntreSolicitacoes()))) {
            LOGGER.atInfo().addKeyValue("event", "auth.password-reset.throttled")
                    .addKeyValue("resourceType", "user")
                    .addKeyValue("resourceId", usuario.getId())
                    .log("Password reset request throttled");
            return;
        }

        passwordResetTokenRepository.invalidarPendentes(usuario.getId(), agora);
        String token = gerarToken();
        passwordResetTokenRepository.save(PasswordResetTokenEntity.builder()
                .user(usuario)
                .tokenHash(hash(token))
                .createdAt(agora)
                .expiresAt(agora.plus(properties.validade()))
                .build());

        UUID idUsuario = usuario.getId();
        String destinatario = usuario.getEmail();
        String nome = usuario.getName();
        String link = UriComponentsBuilder.fromUriString(properties.urlRedefinicao())
                .queryParam("token", token)
                .build()
                .toUriString();
        // O e-mail só é enviado após o commit, garantindo que o token já exista quando o link for usado.
        executarAposCommit(() -> emailRecuperacaoSenha.enviar(
                idUsuario, destinatario, nome, link, properties.validade()));
        businessEventLogger.success("auth.password-reset.requested", "user", idUsuario);
    }

    @Transactional
    public void redefinir(String token, String novaSenha) {
        if (novaSenha == null || novaSenha.isBlank()) {
            throw new IllegalArgumentException("A nova senha é obrigatória");
        }
        if (novaSenha.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha excede o limite de 72 bytes");
        }

        PasswordResetTokenEntity tokenRecuperacao = passwordResetTokenRepository
                .findByTokenHash(hash(token))
                .orElseThrow(() -> new InvalidPasswordResetTokenException(MENSAGEM_TOKEN_INVALIDO));

        LocalDateTime agora = agora();
        if (passwordResetTokenRepository.consumir(tokenRecuperacao.getId(), agora) != 1) {
            throw new InvalidPasswordResetTokenException(MENSAGEM_TOKEN_INVALIDO);
        }

        UserEntity usuario = tokenRecuperacao.getUser();
        if (!usuario.isActive()) {
            throw new InvalidPasswordResetTokenException(MENSAGEM_TOKEN_INVALIDO);
        }

        usuario.setPasswordHash(passwordEncoder.encode(novaSenha));
        usuario.setLoginAttempts(0);
        usuario.setLockedUntil(null);
        userRepository.save(usuario);

        passwordResetTokenRepository.invalidarPendentes(usuario.getId(), agora);
        tokenStateService.revokeAll(usuario.getId()).ifPresent(csrfTokenStore::remover);
        userAccessHistoryService.registrarSenhaAlterada(usuario);
        businessEventLogger.success("auth.password-reset.completed", "user", usuario.getId());
    }

    private String gerarToken() {
        byte[] bytes = new byte[BYTES_TOKEN];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private void executarAposCommit(Runnable acao) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    acao.run();
                }
            });
            return;
        }
        acao.run();
    }
}
