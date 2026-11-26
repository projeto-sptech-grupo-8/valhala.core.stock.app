package valhalla.core.stock.app.modules.auth.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import valhalla.core.stock.app.modules.auth.config.RecuperacaoSenhaProperties;
import valhalla.core.stock.app.modules.auth.entity.PasswordResetTokenEntity;
import valhalla.core.stock.app.modules.auth.repository.PasswordResetTokenRepository;
import valhalla.core.stock.app.modules.auth.security.CsrfTokenStore;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.modules.users.service.UserAccessHistoryService;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;
import valhalla.core.stock.app.shared.mail.EmailProperties;
import valhalla.core.stock.app.shared.mail.ServicoEmail;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecuperacaoSenhaServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenStateService tokenStateService;
    @Mock
    private BusinessEventLogger businessEventLogger;
    @Mock
    private JavaMailSender mailSender;
    @Mock
    private UserAccessHistoryService userAccessHistoryService;
    @Mock
    private CsrfTokenStore csrfTokenStore;

    private RecuperacaoSenhaService service;

    @BeforeEach
    void setUp() {
        ServicoEmail servicoEmail = new ServicoEmail(mailSender,
                new EmailProperties("nao-responda@meraki.com", "Adega Meraki"));
        service = new RecuperacaoSenhaService(
                userRepository,
                passwordResetTokenRepository,
                passwordEncoder,
                tokenStateService,
                new EmailRecuperacaoSenha(servicoEmail),
                new RecuperacaoSenhaProperties(
                        "http://localhost:5173/redefinir-senha",
                        Duration.ofMinutes(30),
                        Duration.ofMinutes(1)
                ),
                businessEventLogger,
                userAccessHistoryService,
                csrfTokenStore
        );
    }

    @Test
    void deveEnviarEmailComLinkCujoTokenCorrespondeAoHashPersistido() throws Exception {
        UserEntity usuario = usuario("<Lucas & Cia>");
        when(userRepository.findByEmailIgnoreCase("lucas@meraki.com")).thenReturn(Optional.of(usuario));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

        service.solicitar(" Lucas@Meraki.com ");

        ArgumentCaptor<PasswordResetTokenEntity> tokenSalvo =
                ArgumentCaptor.forClass(PasswordResetTokenEntity.class);
        verify(passwordResetTokenRepository).save(tokenSalvo.capture());
        verify(passwordResetTokenRepository).invalidarPendentes(eq(usuario.getId()), any());

        ArgumentCaptor<MimeMessage> mensagemEnviada = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(mensagemEnviada.capture());
        MimeMessage mensagem = mensagemEnviada.getValue();
        assertThat(mensagem.getAllRecipients()[0].toString()).isEqualTo("lucas@meraki.com");
        assertThat(mensagem.getFrom()[0].toString()).contains("nao-responda@meraki.com");
        assertThat(mensagem.getSubject()).isEqualTo("Redefinição de senha - Adega Meraki");

        String conteudo = conteudo(mensagem);
        assertThat(conteudo).contains("&lt;Lucas &amp; Cia&gt;").doesNotContain("<Lucas & Cia>!</p>");
        assertThat(conteudo).contains("30 minutos");

        Matcher matcher = Pattern.compile("redefinir-senha\\?token=([A-Za-z0-9_-]{43})").matcher(conteudo);
        assertThat(matcher.find()).isTrue();
        assertThat(RecuperacaoSenhaService.hash(matcher.group(1)))
                .isEqualTo(tokenSalvo.getValue().getTokenHash());
    }

    @Test
    void naoDevePropagarFalhaDoServidorSmtp() {
        UserEntity usuario = usuario("Lucas");
        when(userRepository.findByEmailIgnoreCase("lucas@meraki.com")).thenReturn(Optional.of(usuario));
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        doThrow(new MailSendException("SMTP indisponível")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> service.solicitar("lucas@meraki.com")).doesNotThrowAnyException();
    }

    @Test
    void naoDeveEnviarEmailQuandoUsuarioNaoExiste() {
        when(userRepository.findByEmailIgnoreCase("x@meraki.com")).thenReturn(Optional.empty());

        service.solicitar("x@meraki.com");

        verifyNoInteractions(passwordResetTokenRepository, mailSender);
    }

    @Test
    void deveRegistrarSenhaAlteradaERemoverCsrfAoRedefinirSenha() {
        UserEntity usuario = usuario("Lucas");
        UUID tokenId = UUID.randomUUID();
        UUID sessaoAnterior = UUID.randomUUID();
        PasswordResetTokenEntity tokenEntity = PasswordResetTokenEntity.builder()
                .id(tokenId)
                .user(usuario)
                .tokenHash(RecuperacaoSenhaService.hash("token-secreto"))
                .build();

        when(passwordResetTokenRepository.findByTokenHash(RecuperacaoSenhaService.hash("token-secreto")))
                .thenReturn(Optional.of(tokenEntity));
        when(passwordResetTokenRepository.consumir(eq(tokenId), any())).thenReturn(1);
        when(passwordEncoder.encode("NovaSenha@123")).thenReturn("hash-novo");
        when(tokenStateService.revokeAll(usuario.getId())).thenReturn(Optional.of(sessaoAnterior));

        service.redefinir("token-secreto", "NovaSenha@123");

        verify(userRepository).save(usuario);
        verify(tokenStateService).revokeAll(usuario.getId());
        verify(csrfTokenStore).remover(sessaoAnterior);
        verify(userAccessHistoryService).registrarSenhaAlterada(usuario);
    }

    @Test
    void deveRejeitarNovaSenhaEmBrancoOuMaiorQue72BytesUtf8() {
        assertThatThrownBy(() -> service.redefinir("token", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obrigatória");

        String senhaComAcentos = "á".repeat(40); // 40 chars, 80 bytes UTF-8
        assertThatThrownBy(() -> service.redefinir("token", senhaComAcentos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 bytes");

        verifyNoInteractions(passwordResetTokenRepository);
    }

    private UserEntity usuario(String nome) {
        return UserEntity.builder()
                .id(UUID.randomUUID())
                .name(nome)
                .email("lucas@meraki.com")
                .status(UserStatus.ATIVO)
                .build();
    }

    private String conteudo(MimeMessage mensagem) throws Exception {
        mensagem.saveChanges();
        StringBuilder texto = new StringBuilder();
        coletarTexto(mensagem.getContent(), texto);
        return texto.toString();
    }

    private void coletarTexto(Object conteudo, StringBuilder texto) throws Exception {
        if (conteudo instanceof String parte) {
            texto.append(parte).append('\n');
        } else if (conteudo instanceof MimeMultipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                coletarTexto(multipart.getBodyPart(i).getContent(), texto);
            }
        }
    }
}
