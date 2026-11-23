package valhalla.core.stock.app.shared.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Envia e-mails via Jakarta Mail (JavaMail). Cada mensagem possui uma versão em texto
 * simples e outra em HTML, para clientes de e-mail que não exibem HTML.
 */
@Service
public class ServicoEmail {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    public ServicoEmail(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public void enviar(String destinatario, String assunto, String textoPlano, String html) {
        try {
            MimeMessage mensagem = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mensagem, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.remetente(), properties.nomeRemetente());
            helper.setTo(destinatario);
            helper.setSubject(assunto);
            helper.setText(textoPlano, html);
            mailSender.send(mensagem);
        } catch (MessagingException | UnsupportedEncodingException | MailException exception) {
            throw new FalhaEnvioEmailException("Falha ao enviar e-mail", exception);
        }
    }
}
