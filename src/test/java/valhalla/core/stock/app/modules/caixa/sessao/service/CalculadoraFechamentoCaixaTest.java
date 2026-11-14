package valhalla.core.stock.app.modules.caixa.sessao.service;

import org.junit.jupiter.api.Test;
import valhalla.core.stock.app.modules.caixa.sessao.dto.ResumoFechamentoDto;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.entity.MovimentacaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.sessao.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.sessao.enums.TipoMovimentacaoCaixa;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraFechamentoCaixaTest {

    private final CalculadoraFechamentoCaixa calculadora = new CalculadoraFechamentoCaixa();

    @Test
    void calculaResumoComApenasPagamentosAprovadosSemDescontarTrocoNovamente() {
        SessaoCaixaEntity sessao = sessao("100.00");

        PagamentoPedidoEntity dinheiro = pagamento(
                FormaPagamentoCaixa.DINHEIRO,
                StatusPagamentoPedido.APROVADO,
                "250.00"
        );
        dinheiro.setValorCobrado(new BigDecimal("300.00"));
        dinheiro.setValorRecebido(new BigDecimal("300.00"));
        dinheiro.setValorTroco(new BigDecimal("50.00"));

        List<PagamentoPedidoEntity> pagamentos = List.of(
                dinheiro,
                pagamento(FormaPagamentoCaixa.PIX, StatusPagamentoPedido.APROVADO, "100.00"),
                pagamento(FormaPagamentoCaixa.DEBITO, StatusPagamentoPedido.RECUSADO, "999.00"),
                pagamento(FormaPagamentoCaixa.CREDITO, StatusPagamentoPedido.PENDENTE, "999.00")
        );

        List<MovimentacaoCaixaEntity> movimentacoes = List.of(
                movimentacao(TipoMovimentacaoCaixa.SANGRIA, "50.00"),
                movimentacao(TipoMovimentacaoCaixa.REFORCO, "20.00")
        );

        ResumoFechamentoDto resumo = calculadora.calcularResumo(
                sessao,
                pagamentos,
                movimentacoes,
                new BigDecimal("315.00")
        );

        assertThat(resumo.qtdVendas()).isEqualTo(2);
        assertThat(resumo.totalVendas()).isEqualByComparingTo("350.00");
        assertThat(resumo.porForma())
                .containsEntry(FormaPagamentoCaixa.DINHEIRO.name(), new BigDecimal("250.00"))
                .containsEntry(FormaPagamentoCaixa.PIX.name(), new BigDecimal("100.00"))
                .containsEntry(FormaPagamentoCaixa.DEBITO.name(), new BigDecimal("0.00"))
                .containsEntry(FormaPagamentoCaixa.CREDITO.name(), new BigDecimal("0.00"));
        assertThat(resumo.sangrias()).isEqualByComparingTo("50.00");
        assertThat(resumo.reforcos()).isEqualByComparingTo("20.00");
        assertThat(resumo.fundoTroco()).isEqualByComparingTo("100.00");
        assertThat(resumo.dinheiroEsperado()).isEqualByComparingTo("320.00");
        assertThat(resumo.dinheiroContado()).isEqualByComparingTo("315.00");
        assertThat(resumo.diferenca()).isEqualByComparingTo("-5.00");
    }

    @Test
    void naoCalculaDiferencaQuandoDinheiroContadoNaoFoiInformado() {
        ResumoFechamentoDto resumo = calculadora.calcularResumo(
                sessao("50.00"),
                List.of(),
                List.of(),
                null
        );

        assertThat(resumo.dinheiroEsperado()).isEqualByComparingTo("50.00");
        assertThat(resumo.dinheiroContado()).isNull();
        assertThat(resumo.diferenca()).isNull();
    }

    @Test
    void identificaSomentePagamentosQueBloqueiamOFechamento() {
        List<PagamentoPedidoEntity> pagamentos = List.of(
                pagamento(FormaPagamentoCaixa.PIX, StatusPagamentoPedido.PENDENTE, "10.00"),
                pagamento(FormaPagamentoCaixa.PIX, StatusPagamentoPedido.APROVADO, "20.00"),
                pagamento(FormaPagamentoCaixa.PIX, StatusPagamentoPedido.CANCELADO, "30.00"),
                pagamento(FormaPagamentoCaixa.PIX, StatusPagamentoPedido.ESTORNO_EM_ANDAMENTO, "40.00")
        );

        Map<String, Long> pendencias = calculadora.pendenciasPorStatus(pagamentos);

        assertThat(pendencias)
                .containsEntry(StatusPagamentoPedido.PENDENTE.name(), 1L)
                .containsEntry(StatusPagamentoPedido.ESTORNO_EM_ANDAMENTO.name(), 1L)
                .doesNotContainKeys(
                        StatusPagamentoPedido.APROVADO.name(),
                        StatusPagamentoPedido.CANCELADO.name()
                );
    }

    @Test
    void calculaDinheiroDisponivelComFundoVendasReforcosESangriasAnteriores() {
        BigDecimal disponivel = calculadora.calcularDinheiroDisponivel(
                sessao("100.00"),
                List.of(
                        pagamento(FormaPagamentoCaixa.DINHEIRO, StatusPagamentoPedido.APROVADO, "80.00"),
                        pagamento(FormaPagamentoCaixa.PIX, StatusPagamentoPedido.APROVADO, "500.00")
                ),
                List.of(
                        movimentacao(TipoMovimentacaoCaixa.REFORCO, "10.00"),
                        movimentacao(TipoMovimentacaoCaixa.SANGRIA, "30.00")
                )
        );

        assertThat(disponivel).isEqualByComparingTo("160.00");
    }

    private SessaoCaixaEntity sessao(String fundoTroco) {
        SessaoCaixaEntity sessao = new SessaoCaixaEntity();
        sessao.setId(UUID.randomUUID());
        sessao.setFundoTroco(new BigDecimal(fundoTroco));
        return sessao;
    }

    private PagamentoPedidoEntity pagamento(
            FormaPagamentoCaixa forma,
            StatusPagamentoPedido status,
            String valor
    ) {
        PagamentoPedidoEntity pagamento = new PagamentoPedidoEntity();
        pagamento.setId(UUID.randomUUID());
        pagamento.setPedidoId(UUID.randomUUID());
        pagamento.setForma(forma);
        pagamento.setStatus(status);
        pagamento.setValor(new BigDecimal(valor));
        return pagamento;
    }

    private MovimentacaoCaixaEntity movimentacao(TipoMovimentacaoCaixa tipo, String valor) {
        MovimentacaoCaixaEntity movimentacao = new MovimentacaoCaixaEntity();
        movimentacao.setTipo(tipo);
        movimentacao.setValor(new BigDecimal(valor));
        return movimentacao;
    }
}
