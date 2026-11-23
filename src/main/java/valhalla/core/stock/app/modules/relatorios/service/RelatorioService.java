package valhalla.core.stock.app.modules.relatorios.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.relatorios.dto.PerdasResponseDto;
import valhalla.core.stock.app.modules.relatorios.dto.RelatorioLinhas;
import valhalla.core.stock.app.modules.relatorios.dto.RelatorioResponseDto;
import valhalla.core.stock.app.modules.relatorios.repository.RelatorioRepository;

import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class RelatorioService {

    private static final ZoneId FUSO_HORARIO_OPERACAO = FusoHorarioOperacao.ZONA;

    private final RelatorioRepository repository;

    public RelatorioService(RelatorioRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('VISUALIZAR_RELATORIOS', authentication)")
    public RelatorioResponseDto<RelatorioLinhas.CurvaAbc> curvaAbc(int dias) {
        return montar(dias, repository::curvaAbc);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('VISUALIZAR_RELATORIOS', authentication)")
    public RelatorioResponseDto<RelatorioLinhas.MargemCategoria> margemCategorias(int dias) {
        return montar(dias, repository::margemCategorias);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('VISUALIZAR_RELATORIOS', authentication)")
    public RelatorioResponseDto<RelatorioLinhas.Ruptura> rupturas(int dias) {
        return montar(dias, (estab, de, ate) -> repository.rupturas(estab, de, ate, dias));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('VISUALIZAR_RELATORIOS', authentication)")
    public RelatorioResponseDto<RelatorioLinhas.Horario> horarios(int dias) {
        return montar(dias, repository::horarios);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('VISUALIZAR_RELATORIOS', authentication)")
    public RelatorioResponseDto<RelatorioLinhas.Canal> canais(int dias) {
        return montar(dias, repository::canais);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('VISUALIZAR_RELATORIOS', authentication)")
    public PerdasResponseDto perdas(int dias) {
        String granularidade = dias <= 7 ? "DIA" : dias <= 30 ? "SEMANA" : "MES";
        String truncamento = dias <= 7 ? "day" : dias <= 30 ? "week" : "month";
        RelatorioResponseDto<RelatorioLinhas.Perda> r =
                montar(dias, (estab, de, ate) -> repository.perdas(estab, de, ate, truncamento));
        return new PerdasResponseDto(r.referencia(), r.totais(), granularidade, r.itens());
    }

    @FunctionalInterface
    private interface Consulta<T> {
        List<T> executar(UUID estab, LocalDateTime de, LocalDateTime ate);
    }

    /** Período = os últimos {@code dias} dias, incluindo hoje. */
    private <T> RelatorioResponseDto<T> montar(int dias, Consulta<T> consulta) {
        UUID estab = obterIdEstabelecimentoAutenticado();
        LocalDateTime ate = LocalDateTime.now(FUSO_HORARIO_OPERACAO).toLocalDate().plusDays(1).atStartOfDay();
        LocalDateTime de = ate.minusDays(dias);
        return new RelatorioResponseDto<>(
                new RelatorioResponseDto.Referencia(dias, de.toLocalDate(), ate.toLocalDate().minusDays(1),
                        FUSO_HORARIO_OPERACAO.getId()),
                repository.totais(estab, de, ate),
                consulta.executar(estab, de, ate));
    }

    private UUID obterIdEstabelecimentoAutenticado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Sessão inválida");
        }
        try {
            String idEstabelecimento = token.getToken().getClaimAsString("establishmentId");
            if (idEstabelecimento == null || idEstabelecimento.isBlank()) {
                throw new AccessDeniedException("Sessão inválida");
            }
            return UUID.fromString(idEstabelecimento);
        } catch (IllegalArgumentException excecao) {
            throw new AccessDeniedException("Sessão inválida");
        }
    }
}
