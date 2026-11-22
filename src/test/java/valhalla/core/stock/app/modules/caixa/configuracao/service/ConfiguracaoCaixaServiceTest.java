package valhalla.core.stock.app.modules.caixa.configuracao.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import valhalla.core.stock.app.modules.caixa.configuracao.dto.ConfiguracaoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.configuracao.dto.ConfiguracaoCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.PoliticaJurosCaixa;
import valhalla.core.stock.app.modules.caixa.configuracao.mapper.ConfiguracaoCaixaMapper;
import valhalla.core.stock.app.modules.caixa.configuracao.repository.ConfiguracaoCaixaRepository;
import valhalla.core.stock.app.modules.caixa.exception.ConfiguracaoCaixaInvalidaException;
import valhalla.core.stock.app.modules.caixa.security.ContextoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.service.AuditoriaCaixaService;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfiguracaoCaixaServiceTest {

    @Mock private ConfiguracaoCaixaRepository configuracaoCaixaRepository;
    @Mock private DisponibilidadePoint disponibilidadePoint;
    @Mock private AuditoriaCaixaService auditoriaCaixaService;
    @Mock private ContextoCaixa contextoCaixa;
    @Mock private BusinessEventLogger businessEventLogger;

    @InjectMocks
    private ConfiguracaoCaixaService service;

    @Test
    void obterConfiguracaoRetornaExistenteOuPadrao() {
        UUID estabelecimentoId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabelecimentoId);
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabelecimentoId))
                .thenReturn(Optional.empty());

        ConfiguracaoCaixaResponseDto padrao = service.obterConfiguracao();
        assertThat(padrao.dinheiroHabilitado()).isTrue();
        assertThat(padrao.pointHabilitado()).isFalse();

        ConfiguracaoCaixaEntity existente = ConfiguracaoCaixaMapper.criarPadrao(estabelecimentoId);
        existente.setId(UUID.randomUUID());
        existente.setParcelasMaximas(6);
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabelecimentoId))
                .thenReturn(Optional.of(existente));

        ConfiguracaoCaixaResponseDto obtida = service.obterConfiguracao();
        assertThat(obtida.parcelasMaximas()).isEqualTo(6);
    }

    @Test
    void atualizarConfiguracaoValidaTodasAsRegrasDeNegocio() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabelecimentoId);
        when(contextoCaixa.usuarioId()).thenReturn(usuarioId);

        assertThatThrownBy(() -> service.atualizarConfiguracao(request(
                false, false, false, false, false, false, 1, 1, null
        ))).isInstanceOf(ConfiguracaoCaixaInvalidaException.class)
                .hasMessage("Pelo menos uma forma de pagamento deve estar habilitada");

        when(disponibilidadePoint.possuiTerminalAtivo(estabelecimentoId)).thenReturn(false);
        assertThatThrownBy(() -> service.atualizarConfiguracao(request(
                true, true, true, true, true, false, 1, 5, null
        ))).isInstanceOf(ConfiguracaoCaixaInvalidaException.class)
                .hasMessage("Cadastre ao menos um terminal ativo para habilitar a Point");

        assertThatThrownBy(() -> service.atualizarConfiguracao(request(
                false, true, true, false, true, true, 1, 5, null
        ))).isInstanceOf(ConfiguracaoCaixaInvalidaException.class)
                .hasMessage("O parcelamento exige a forma de pagamento crédito habilitada");

        assertThatThrownBy(() -> service.atualizarConfiguracao(request(
                false, true, true, true, true, true, 4, 2, null
        ))).isInstanceOf(ConfiguracaoCaixaInvalidaException.class)
                .hasMessage("O número máximo de parcelas deve ser maior ou igual ao mínimo");

        assertThatThrownBy(() -> service.atualizarConfiguracao(request(
                false, true, true, true, true, true, 1, 13, null
        ))).isInstanceOf(ConfiguracaoCaixaInvalidaException.class)
                .hasMessage("O número máximo de parcelas deve ser no máximo 12");

        assertThatThrownBy(() -> service.atualizarConfiguracao(request(
                false, true, true, true, true, true, 1, 6, BigDecimal.ZERO
        ))).isInstanceOf(ConfiguracaoCaixaInvalidaException.class)
                .hasMessage("O valor mínimo da parcela deve ser maior que zero");
    }

    @Test
    void atualizarConfiguracaoComESemAlteracaoControlaAuditoria() {
        UUID estabelecimentoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(contextoCaixa.estabelecimentoId()).thenReturn(estabelecimentoId);
        when(contextoCaixa.usuarioId()).thenReturn(usuarioId);
        when(disponibilidadePoint.possuiTerminalAtivo(estabelecimentoId)).thenReturn(true);

        ConfiguracaoCaixaEntity existente = ConfiguracaoCaixaMapper.criarPadrao(estabelecimentoId);
        existente.setId(UUID.randomUUID());
        when(configuracaoCaixaRepository.findByEstabelecimentoId(estabelecimentoId))
                .thenReturn(Optional.of(existente));
        when(configuracaoCaixaRepository.saveAndFlush(any(ConfiguracaoCaixaEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Mesmo estado -> não grava auditoria
        ConfiguracaoCaixaRequestDto igualAoPadrao = new ConfiguracaoCaixaRequestDto(
                existente.isPointHabilitado(),
                existente.isDinheiroHabilitado(),
                existente.isDebitoHabilitado(),
                existente.isCreditoHabilitado(),
                existente.isPixHabilitado(),
                existente.isParcelamentoHabilitado(),
                existente.getParcelasMinimas(),
                existente.getParcelasMaximas(),
                existente.getValorMinimoParcela(),
                existente.getPoliticaJuros(),
                existente.getValidadeCobrancaSegundos(),
                existente.getLimiteDescontoPercentual(),
                existente.getLimiteDescontoValor(),
                existente.isImprimirComprovante(),
                existente.isPermitirPagamentoManual()
        );
        service.atualizarConfiguracao(igualAoPadrao);
        verify(auditoriaCaixaService, never()).registrar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        // Estado alterado -> grava auditoria e evento de negócio
        ConfiguracaoCaixaResponseDto alterado = service.atualizarConfiguracao(request(
                true, true, true, true, true, true, 1, 10, new BigDecimal("15.00")
        ));
        assertThat(alterado.pointHabilitado()).isTrue();
        assertThat(alterado.parcelasMaximas()).isEqualTo(10);
        verify(auditoriaCaixaService).registrar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(businessEventLogger).success("caixa.configuration.updated", "caixa_configuration", existente.getId());
    }

    private ConfiguracaoCaixaRequestDto request(
            boolean point,
            boolean dinheiro,
            boolean debito,
            boolean credito,
            boolean pix,
            boolean parcelamento,
            int minParcelas,
            int maxParcelas,
            BigDecimal valorMinimoParcela
    ) {
        return new ConfiguracaoCaixaRequestDto(
                point, dinheiro, debito, credito, pix,
                parcelamento, minParcelas, maxParcelas, valorMinimoParcela,
                PoliticaJurosCaixa.ABSORVIDA_PELA_LOJA,
                300,
                new BigDecimal("10.00"),
                new BigDecimal("20.00"),
                true,
                false
        );
    }
}
