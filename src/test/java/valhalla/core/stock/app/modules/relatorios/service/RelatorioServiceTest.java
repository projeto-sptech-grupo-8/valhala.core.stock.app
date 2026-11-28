package valhalla.core.stock.app.modules.relatorios.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import valhalla.core.stock.app.modules.relatorios.dto.PerdasResponseDto;
import valhalla.core.stock.app.modules.relatorios.dto.RelatorioLinhas;
import valhalla.core.stock.app.modules.relatorios.dto.RelatorioResponseDto;
import valhalla.core.stock.app.modules.relatorios.repository.RelatorioRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelatorioServiceTest {

    @Mock
    private RelatorioRepository repository;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void delegaTodosOsRelatoriosEGranularidadesDePerdas() {
        UUID estab = UUID.randomUUID();
        autenticarComClaim(estab.toString());
        RelatorioService service = new RelatorioService(repository);
        RelatorioResponseDto.Totais totais = new RelatorioResponseDto.Totais(2L, new BigDecimal("100.00"));
        when(repository.totais(eq(estab), any(), any())).thenReturn(totais);
        when(repository.curvaAbc(eq(estab), any(), any())).thenReturn(List.of());
        when(repository.margemCategorias(eq(estab), any(), any())).thenReturn(List.of());
        when(repository.rupturas(eq(estab), any(), any(), eq(15))).thenReturn(List.of());
        when(repository.horarios(eq(estab), any(), any())).thenReturn(List.of());
        when(repository.canais(eq(estab), any(), any())).thenReturn(List.of());
        when(repository.perdas(eq(estab), any(), any(), any())).thenReturn(List.of());

        assertThat(service.curvaAbc(7).totais()).isEqualTo(totais);
        assertThat(service.margemCategorias(7).totais()).isEqualTo(totais);
        assertThat(service.rupturas(15).referencia().dias()).isEqualTo(15);
        assertThat(service.horarios(7).totais()).isEqualTo(totais);
        assertThat(service.canais(7).totais()).isEqualTo(totais);

        PerdasResponseDto diario = service.perdas(7);
        assertThat(diario.granularidade()).isEqualTo("DIA");
        verify(repository).perdas(eq(estab), any(), any(), eq("day"));

        PerdasResponseDto semanal = service.perdas(30);
        assertThat(semanal.granularidade()).isEqualTo("SEMANA");
        verify(repository).perdas(eq(estab), any(), any(), eq("week"));

        PerdasResponseDto mensal = service.perdas(90);
        assertThat(mensal.granularidade()).isEqualTo("MES");
        verify(repository).perdas(eq(estab), any(), any(), eq("month"));

        List<RelatorioLinhas.Perda> itens = mensal.itens();
        assertThat(itens).isEmpty();
    }

    @Test
    void rejeitaSessaoSemJwtOuComEstablishmentIdInvalido() {
        RelatorioService service = new RelatorioService(repository);

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.curvaAbc(7))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Sessão inválida");

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", "pw"));
        assertThatThrownBy(() -> service.curvaAbc(7))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Sessão inválida");

        autenticarComClaim(null);
        assertThatThrownBy(() -> service.curvaAbc(7))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Sessão inválida");

        autenticarComClaim("   ");
        assertThatThrownBy(() -> service.curvaAbc(7))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Sessão inválida");

        autenticarComClaim("nao-eh-uuid");
        assertThatThrownBy(() -> service.curvaAbc(7))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Sessão inválida");
    }

    private void autenticarComClaim(String establishmentId) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .issuedAt(Instant.now());
        if (establishmentId != null) {
            builder.claim("establishmentId", establishmentId);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build()));
    }
}
