package valhalla.core.stock.app.modules.caixa.pedido.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.pedido.dto.RegistrarPagamentoPedidoRequestDto;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.sessao.entity.SessaoCaixaEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessadorPagamentoDinheiroTest {

    private ProcessadorPagamentoDinheiro processador;
    private PedidoEntity pedido;
    private SessaoCaixaEntity sessao;

    @BeforeEach
    void configurar() {
        processador = new ProcessadorPagamentoDinheiro();
        pedido = new PedidoEntity();
        pedido.setId(UUID.randomUUID());
        sessao = new SessaoCaixaEntity();
        sessao.setId(UUID.randomUUID());
    }

    @Test
    void suportaApenasDinheiro() {
        ConfiguracaoCaixaEntity config = new ConfiguracaoCaixaEntity();
        assertThat(processador.suporta(FormaPagamentoCaixa.DINHEIRO, config)).isTrue();
        assertThat(processador.suporta(FormaPagamentoCaixa.PIX, config)).isFalse();
        assertThat(processador.suporta(FormaPagamentoCaixa.DEBITO, config)).isFalse();
        assertThat(processador.suporta(FormaPagamentoCaixa.CREDITO, config)).isFalse();
    }

    @Test
    void processaPagamentoEmDinheiroComTrocoEValidacoes() {
        ConfiguracaoCaixaEntity configDesabilitado = new ConfiguracaoCaixaEntity();
        configDesabilitado.setDinheiroHabilitado(false);
        assertThatThrownBy(() -> processador.processar(
                pedido,
                sessao,
                configDesabilitado,
                new RegistrarPagamentoPedidoRequestDto(
                        FormaPagamentoCaixa.DINHEIRO, new BigDecimal("50.00"), null, new BigDecimal("50.00"), null, null
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("desabilitado");

        ConfiguracaoCaixaEntity config = new ConfiguracaoCaixaEntity();
        config.setDinheiroHabilitado(true);

        assertThatThrownBy(() -> processador.processar(
                pedido,
                sessao,
                config,
                new RegistrarPagamentoPedidoRequestDto(
                        FormaPagamentoCaixa.DINHEIRO, new BigDecimal("50.00"), 2, new BigDecimal("50.00"), null, null
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parcelamento");

        assertThatThrownBy(() -> processador.processar(
                pedido,
                sessao,
                config,
                new RegistrarPagamentoPedidoRequestDto(
                        FormaPagamentoCaixa.DINHEIRO, new BigDecimal("50.00"), 1, null, null, null
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valor recebido");

        assertThatThrownBy(() -> processador.processar(
                pedido,
                sessao,
                config,
                new RegistrarPagamentoPedidoRequestDto(
                        FormaPagamentoCaixa.DINHEIRO, new BigDecimal("50.00"), 1, new BigDecimal("40.00"), null, null
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menor que o valor da parcela");

        PagamentoPedidoEntity aprovado = processador.processar(
                pedido,
                sessao,
                config,
                new RegistrarPagamentoPedidoRequestDto(
                        FormaPagamentoCaixa.DINHEIRO,
                        new BigDecimal("45.00"),
                        null,
                        new BigDecimal("100.00"),
                        "  idem-dinheiro  ",
                        "   "
                )
        );
        assertThat(aprovado.getStatus()).isEqualTo(StatusPagamentoPedido.APROVADO);
        assertThat(aprovado.getProvedor()).isEqualTo("INTERNO");
        assertThat(aprovado.getValorTroco()).isEqualByComparingTo("55.00");
        assertThat(aprovado.getChaveIdempotencia()).isEqualTo("idem-dinheiro");
        assertThat(aprovado.getComprovanteReferencia()).isNull();
        assertThat(aprovado.getPagoEm()).isNotNull();
    }
}
