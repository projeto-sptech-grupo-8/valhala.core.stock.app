package valhalla.core.stock.app.shared.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Habilita o envio assíncrono de e-mails, evitando que a latência do servidor SMTP
 * afete o tempo de resposta das requisições.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfiguration {
}
