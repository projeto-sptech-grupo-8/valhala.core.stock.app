package valhalla.core.stock.app.modules.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import valhalla.core.stock.app.shared.mail.FalhaEnvioEmailException;
import valhalla.core.stock.app.shared.mail.ServicoEmail;

import java.time.Duration;
import java.util.UUID;

/**
 * Monta e envia, de forma assíncrona, o e-mail com o link de redefinição de senha.
 * Falhas são apenas registradas: a resposta da solicitação nunca revela se o e-mail existe.
 */
@Component
public class EmailRecuperacaoSenha {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailRecuperacaoSenha.class);
    private static final String ASSUNTO = "Redefinição de senha - Adega Meraki";

    private final ServicoEmail servicoEmail;

    public EmailRecuperacaoSenha(ServicoEmail servicoEmail) {
        this.servicoEmail = servicoEmail;
    }

    @Async
    public void enviar(UUID idUsuario, String destinatario, String nome, String link, Duration validade) {
        long minutos = Math.max(1, validade.toMinutes());
        try {
            servicoEmail.enviar(destinatario, ASSUNTO,
                    textoPlano(nome, link, minutos), html(nome, link, minutos));
            LOGGER.atInfo().addKeyValue("event", "auth.password-reset.email-sent")
                    .addKeyValue("resourceType", "user")
                    .addKeyValue("resourceId", idUsuario)
                    .log("Password reset e-mail sent");
        } catch (FalhaEnvioEmailException exception) {
            LOGGER.atError().addKeyValue("event", "auth.password-reset.email-failed")
                    .addKeyValue("resourceType", "user")
                    .addKeyValue("resourceId", idUsuario)
                    .setCause(exception)
                    .log("Password reset e-mail could not be sent");
        }
    }

    private String textoPlano(String nome, String link, long minutos) {
        return """
                Olá, %s!

                Recebemos uma solicitação para redefinir a senha da sua conta na Adega Meraki.
                Para criar uma nova senha, acesse o link abaixo:

                %s

                O link é válido por %d minutos e pode ser utilizado apenas uma vez.
                Se você não fez essa solicitação, ignore este e-mail: sua senha atual continuará válida.
                """.formatted(nome, link, minutos);
    }

    private String html(String nome, String link, long minutos) {
        String nomeSeguro = HtmlUtils.htmlEscape(nome);
        String linkSeguro = HtmlUtils.htmlEscape(link);
        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <body style="margin:0;padding:24px;background:#f4f4f5;font-family:Arial,Helvetica,sans-serif;color:#18181b;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0">
                    <tr><td align="center">
                      <table role="presentation" width="560" cellspacing="0" cellpadding="0"
                             style="max-width:560px;background:#ffffff;border-radius:8px;padding:32px;">
                        <tr><td>
                          <h1 style="font-size:20px;margin:0 0 16px;">Redefinição de senha</h1>
                          <p style="margin:0 0 16px;">Olá, %s!</p>
                          <p style="margin:0 0 24px;">Recebemos uma solicitação para redefinir a senha da sua conta na Adega Meraki.</p>
                          <p style="margin:0 0 24px;">
                            <a href="%s" style="display:inline-block;background:#18181b;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:6px;">Redefinir senha</a>
                          </p>
                          <p style="margin:0 0 8px;font-size:13px;color:#52525b;">Se o botão não funcionar, copie e cole este endereço no navegador:</p>
                          <p style="margin:0 0 24px;font-size:13px;word-break:break-all;"><a href="%s">%s</a></p>
                          <p style="margin:0 0 8px;font-size:13px;color:#52525b;">O link é válido por %d minutos e pode ser utilizado apenas uma vez.</p>
                          <p style="margin:0;font-size:13px;color:#52525b;">Se você não fez essa solicitação, ignore este e-mail: sua senha atual continuará válida.</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(nomeSeguro, linkSeguro, linkSeguro, linkSeguro, minutos);
    }
}
