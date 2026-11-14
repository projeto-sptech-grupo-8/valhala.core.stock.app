package valhalla.core.stock.app.modules.caixa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.caixa.client.MercadoPagoPointClient;
import valhalla.core.stock.app.modules.caixa.client.dto.ModoOperacaoTerminalPoint;
import valhalla.core.stock.app.modules.caixa.client.dto.TerminalMercadoPagoDto;
import valhalla.core.stock.app.modules.caixa.dto.ModoOperacaoTerminalResponseDto;
import valhalla.core.stock.app.modules.caixa.entity.TerminalCaixaEntity;
import valhalla.core.stock.app.modules.caixa.repository.TerminalCaixaRepository;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TerminalPointService {

    private final TerminalCaixaRepository terminalCaixaRepository;
    private final MercadoPagoPointClient mercadoPagoPointClient;
    private final AuditoriaCaixaService auditoriaCaixaService;
    private final ContextoCaixa contextoCaixa;
    private final BusinessEventLogger businessEventLogger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TerminalPointService(
            TerminalCaixaRepository terminalCaixaRepository,
            MercadoPagoPointClient mercadoPagoPointClient,
            AuditoriaCaixaService auditoriaCaixaService,
            ContextoCaixa contextoCaixa,
            BusinessEventLogger businessEventLogger
    ) {
        this.terminalCaixaRepository = terminalCaixaRepository;
        this.mercadoPagoPointClient = mercadoPagoPointClient;
        this.auditoriaCaixaService = auditoriaCaixaService;
        this.contextoCaixa = contextoCaixa;
        this.businessEventLogger = businessEventLogger;
    }

    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_CONFIGURAR',
                authentication
            )
            """)
    public List<TerminalMercadoPagoDto> listarTerminaisMercadoPago() {
        return mercadoPagoPointClient.listarTerminais();
    }

    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_CONFIGURAR',
                authentication
            )
            """)
    public ModoOperacaoTerminalResponseDto atualizarModoOperacao(
            UUID terminalCaixaId,
            ModoOperacaoTerminalPoint modoOperacao
    ) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        TerminalCaixaEntity terminal = terminalCaixaRepository.findById(terminalCaixaId)
                .orElseThrow(() -> new EntityNotFoundException("Terminal de caixa não encontrado"));
        if (!terminal.getCaixa().getEstabelecimentoId().equals(estabelecimentoId)) {
            throw new AccessDeniedException("Acesso negado ao terminal informado");
        }

        TerminalMercadoPagoDto atualizado = mercadoPagoPointClient.atualizarModoOperacaoTerminal(
                terminal.getTerminalId(),
                modoOperacao
        );

        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                null,
                EventoAuditoriaCaixa.TERMINAL_CAIXA_ALTERADO,
                EventoAuditoriaCaixa.ENTIDADE_TERMINAL_CAIXA,
                terminal.getId().toString(),
                "Modo de operação do terminal " + terminal.getNome() + " alterado para " + modoOperacao,
                null,
                null,
                null,
                objectMapper.valueToTree(Map.of(
                        "terminalId", terminal.getTerminalId(),
                        "modoOperacao", atualizado.operatingMode() == null ? modoOperacao.name() : atualizado.operatingMode()
                ))
        );

        businessEventLogger.success("cash.terminal.mode.updated", "cash_terminal", terminal.getId());

        ModoOperacaoTerminalPoint modoEfetivo = atualizado.operatingMode() == null
                ? modoOperacao
                : ModoOperacaoTerminalPoint.valueOf(atualizado.operatingMode());

        return new ModoOperacaoTerminalResponseDto(
                terminal.getId(),
                terminal.getCaixa().getId(),
                terminal.getTerminalId(),
                terminal.getNome(),
                modoEfetivo
        );
    }
}
