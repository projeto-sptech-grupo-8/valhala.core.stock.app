package valhalla.core.stock.app.modules.caixa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.dto.ConfiguracaoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.dto.ConfiguracaoCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.exception.ConfiguracaoCaixaInvalidaException;
import valhalla.core.stock.app.modules.caixa.mapper.ConfiguracaoCaixaMapper;
import valhalla.core.stock.app.modules.caixa.repository.ConfiguracaoCaixaRepository;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.util.Map;
import java.util.UUID;

@Service
public class ConfiguracaoCaixaService {

    private static final int LIMITE_MAXIMO_PARCELAS = 12;

    private final ConfiguracaoCaixaRepository configuracaoCaixaRepository;
    private final DisponibilidadePoint disponibilidadePoint;
    private final AuditoriaCaixaService auditoriaCaixaService;
    private final ContextoCaixa contextoCaixa;
    private final BusinessEventLogger businessEventLogger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConfiguracaoCaixaService(
            ConfiguracaoCaixaRepository configuracaoCaixaRepository,
            DisponibilidadePoint disponibilidadePoint,
            AuditoriaCaixaService auditoriaCaixaService,
            ContextoCaixa contextoCaixa,
            BusinessEventLogger businessEventLogger
    ) {
        this.configuracaoCaixaRepository = configuracaoCaixaRepository;
        this.disponibilidadePoint = disponibilidadePoint;
        this.auditoriaCaixaService = auditoriaCaixaService;
        this.contextoCaixa = contextoCaixa;
        this.businessEventLogger = businessEventLogger;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_VISUALIZAR',
                authentication
            )
            """)
    public ConfiguracaoCaixaResponseDto obterConfiguracao() {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        ConfiguracaoCaixaEntity configuracao = configuracaoCaixaRepository
                .findByEstabelecimentoId(estabelecimentoId)
                .orElseGet(() -> ConfiguracaoCaixaMapper.criarPadrao(estabelecimentoId));
        return ConfiguracaoCaixaMapper.paraResposta(configuracao);
    }

    @Transactional
    @PreAuthorize("""
            @permissionAuthorizationService.hasPermission(
                'CAIXA_CONFIGURAR',
                authentication
            )
            """)
    public ConfiguracaoCaixaResponseDto atualizarConfiguracao(ConfiguracaoCaixaRequestDto dto) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        UUID usuarioId = contextoCaixa.usuarioId();

        validar(dto, estabelecimentoId);

        ConfiguracaoCaixaEntity configuracao = configuracaoCaixaRepository
                .findByEstabelecimentoId(estabelecimentoId)
                .orElseGet(() -> ConfiguracaoCaixaMapper.criarPadrao(estabelecimentoId));

        boolean houveAlteracao = !ConfiguracaoCaixaMapper.mesmoEstado(configuracao, dto);
        Map<String, Object> dadosAnteriores = ConfiguracaoCaixaMapper.snapshot(configuracao);
        ConfiguracaoCaixaMapper.aplicar(configuracao, dto);
        configuracao.setAtualizadoPorUsuarioId(usuarioId);

        ConfiguracaoCaixaEntity configuracaoSalva = configuracaoCaixaRepository.saveAndFlush(configuracao);

        if (!houveAlteracao) {
            return ConfiguracaoCaixaMapper.paraResposta(configuracaoSalva);
        }

        Map<String, Object> dadosNovos = ConfiguracaoCaixaMapper.snapshot(configuracaoSalva);

        auditoriaCaixaService.registrar(
                estabelecimentoId,
                usuarioId,
                null,
                EventoAuditoriaCaixa.CONFIGURACAO_CAIXA_ALTERADA,
                EventoAuditoriaCaixa.ENTIDADE_CONFIGURACAO_CAIXA,
                configuracaoSalva.getId().toString(),
                "Configuração do caixa atualizada",
                null,
                null,
                objectMapper.valueToTree(dadosAnteriores),
                objectMapper.valueToTree(dadosNovos)
        );

        businessEventLogger.success(
                "caixa.configuration.updated",
                "caixa_configuration",
                configuracaoSalva.getId()
        );

        return ConfiguracaoCaixaMapper.paraResposta(configuracaoSalva);
    }

    private void validar(ConfiguracaoCaixaRequestDto dto, UUID estabelecimentoId) {
        if (!dto.dinheiroHabilitado()
                && !dto.debitoHabilitado()
                && !dto.creditoHabilitado()
                && !dto.pixHabilitado()) {
            throw new ConfiguracaoCaixaInvalidaException(
                    "Pelo menos uma forma de pagamento deve estar habilitada"
            );
        }

        if (dto.pointHabilitado() && !disponibilidadePoint.possuiTerminalAtivo(estabelecimentoId)) {
            throw new ConfiguracaoCaixaInvalidaException(
                    "Cadastre ao menos um terminal ativo para habilitar a Point"
            );
        }

        if (dto.parcelamentoHabilitado() && !dto.creditoHabilitado()) {
            throw new ConfiguracaoCaixaInvalidaException(
                    "O parcelamento exige a forma de pagamento crédito habilitada"
            );
        }

        if (dto.parcelasMaximas() < dto.parcelasMinimas()) {
            throw new ConfiguracaoCaixaInvalidaException(
                    "O número máximo de parcelas deve ser maior ou igual ao mínimo"
            );
        }

        if (dto.parcelasMaximas() > LIMITE_MAXIMO_PARCELAS) {
            throw new ConfiguracaoCaixaInvalidaException(
                    "O número máximo de parcelas deve ser no máximo " + LIMITE_MAXIMO_PARCELAS
            );
        }

        if (dto.valorMinimoParcela() != null
                && dto.valorMinimoParcela().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new ConfiguracaoCaixaInvalidaException(
                    "O valor mínimo da parcela deve ser maior que zero"
            );
        }
    }
}
