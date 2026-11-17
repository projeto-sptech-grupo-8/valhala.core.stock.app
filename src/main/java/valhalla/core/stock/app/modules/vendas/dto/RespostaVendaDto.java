package valhalla.core.stock.app.modules.vendas.dto;

import valhalla.core.stock.app.modules.vendas.entity.CanalVenda;
import valhalla.core.stock.app.modules.vendas.entity.FormaPagamento;
import valhalla.core.stock.app.modules.vendas.entity.StatusPagamento;
import valhalla.core.stock.app.modules.vendas.entity.StatusPedido;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** troco: diferença entre o recebido e o total (só tem valor na resposta da finalização). */
public record RespostaVendaDto(
        UUID id,
        long numero,
        CanalVenda canal,
        StatusPedido status,
        BigDecimal subtotal,
        BigDecimal desconto,
        BigDecimal total,
        BigDecimal troco,
        String nomeCliente,
        String documentoCliente,
        String observacao,
        UUID operadorId,
        LocalDateTime concluidoEm,
        LocalDateTime canceladoEm,
        String motivoCancelamento,
        List<Item> itens,
        List<Pagamento> pagamentos
) {

    public record Item(
            Long id,
            UUID produtoId,
            String descricao,
            BigDecimal quantidade,
            BigDecimal precoUnitario,
            BigDecimal subtotal
    ) {
    }

    public record Pagamento(FormaPagamento forma, StatusPagamento status, BigDecimal valor) {
    }
}
