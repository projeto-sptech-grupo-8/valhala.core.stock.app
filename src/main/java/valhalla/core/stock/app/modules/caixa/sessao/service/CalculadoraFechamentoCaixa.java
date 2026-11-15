package valhalla.core.stock.app.modules.caixa.sessao.service;

import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.caixa.sessao.dto.ResumoFechamentoDto;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.sessao.entity.MovimentacaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.sessao.entity.SessaoCaixaEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.sessao.enums.TipoMovimentacaoCaixa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class CalculadoraFechamentoCaixa {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public ResumoFechamentoDto calcularResumo(
            SessaoCaixaEntity sessao,
            List<PagamentoPedidoEntity> pagamentos,
            List<MovimentacaoCaixaEntity> movimentacoes,
            BigDecimal dinheiroContado
    ) {
        Map<String, BigDecimal> porForma = new LinkedHashMap<>();
        for (FormaPagamentoCaixa forma : FormaPagamentoCaixa.values()) {
            porForma.put(forma.name(), ZERO);
        }

        BigDecimal totalVendas = ZERO;
        long qtdVendas = 0;

        for (PagamentoPedidoEntity pagamento : pagamentos) {
            if (!pagamento.estaAprovado()) {
                continue;
            }

            BigDecimal valor = escala(pagamento.getValor());
            totalVendas = totalVendas.add(valor);
            if (pagamento.getForma() != null) {
                porForma.merge(pagamento.getForma().name(), valor, BigDecimal::add);
            }
        }

        qtdVendas = pagamentos.stream()
                .filter(PagamentoPedidoEntity::estaAprovado)
                .map(PagamentoPedidoEntity::getPedidoId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        BigDecimal sangrias = somarMovimentacoes(movimentacoes, TipoMovimentacaoCaixa.SANGRIA);
        BigDecimal reforcos = somarMovimentacoes(movimentacoes, TipoMovimentacaoCaixa.REFORCO);
        BigDecimal fundoTroco = escala(sessao.getFundoTroco());
        BigDecimal dinheiroEsperado = fundoTroco
                .add(porForma.get(FormaPagamentoCaixa.DINHEIRO.name()))
                .add(reforcos)
                .subtract(sangrias)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal contado = dinheiroContado == null ? null : escala(dinheiroContado);
        BigDecimal diferenca = contado == null
                ? null
                : contado.subtract(dinheiroEsperado).setScale(2, RoundingMode.HALF_UP);

        return new ResumoFechamentoDto(
                qtdVendas,
                totalVendas.setScale(2, RoundingMode.HALF_UP),
                porForma,
                sangrias,
                reforcos,
                fundoTroco,
                dinheiroEsperado,
                contado,
                diferenca
        );
    }

    public BigDecimal calcularDinheiroDisponivel(
            SessaoCaixaEntity sessao,
            List<PagamentoPedidoEntity> pagamentos,
            List<MovimentacaoCaixaEntity> movimentacoes
    ) {
        return calcularResumo(sessao, pagamentos, movimentacoes, null).dinheiroEsperado();
    }

    public Map<String, Long> pendenciasPorStatus(List<PagamentoPedidoEntity> pagamentos) {
        Map<String, Long> pendencias = new LinkedHashMap<>();
        for (StatusPagamentoPedido status : StatusPagamentoPedido.values()) {
            if (!status.bloqueiaFechamento()) {
                continue;
            }
            long quantidade = pagamentos.stream()
                    .filter(pagamento -> pagamento.getStatus() == status)
                    .count();
            if (quantidade > 0) {
                pendencias.put(status.name(), quantidade);
            }
        }
        return pendencias;
    }

    private BigDecimal somarMovimentacoes(
            List<MovimentacaoCaixaEntity> movimentacoes,
            TipoMovimentacaoCaixa tipo
    ) {
        return movimentacoes.stream()
                .filter(movimentacao -> movimentacao.getTipo() == tipo)
                .map(MovimentacaoCaixaEntity::getValor)
                .map(this::escala)
                .reduce(ZERO, BigDecimal::add);
    }

    private BigDecimal escala(BigDecimal valor) {
        return valor == null ? ZERO : valor.setScale(2, RoundingMode.HALF_UP);
    }
}
