package valhalla.core.stock.app.modules.caixa.pedido.mapper;

import valhalla.core.stock.app.modules.caixa.fiscal.dto.NotaFiscalPedidoVinculadaDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.ItemPedidoCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PagamentoPedidoResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaDetalheResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.dto.PedidoCaixaResumoResponseDto;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class PedidoCaixaMapper {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private PedidoCaixaMapper() {
    }

    public static ItemPedidoCaixaResponseDto paraItemResposta(ItemPedidoEntity item) {
        return new ItemPedidoCaixaResponseDto(
                item.getId(),
                item.getProdutoId(),
                item.getDescricaoProduto(),
                item.getQuantidade(),
                item.getPrecoUnitario(),
                item.getCustoUnitario(),
                item.getDesconto(),
                item.getSubtotal(),
                item.getCategoriaId(),
                item.getCategoriaNome()
        );
    }

    public static PedidoCaixaResumoResponseDto paraResumo(
            PedidoEntity pedido,
            String operadorNome,
            int quantidadeItens,
            List<PagamentoPedidoEntity> pagamentos
    ) {
        return paraResumo(pedido, operadorNome, quantidadeItens, pagamentos, null);
    }

    public static PedidoCaixaResumoResponseDto paraResumo(
            PedidoEntity pedido,
            String operadorNome,
            int quantidadeItens,
            List<PagamentoPedidoEntity> pagamentos,
            NotaFiscalPedidoVinculadaDto notaFiscal
    ) {
        BigDecimal totalPago = calcularTotalPagoAprovado(pagamentos);
        BigDecimal saldoRestante = calcularSaldoRestante(pedido.getTotal(), totalPago);
        return new PedidoCaixaResumoResponseDto(
                pedido.getId(),
                pedido.getNumero(),
                pedido.getSessaoCaixaId(),
                pedido.getOperadorUsuarioId(),
                operadorNome,
                pedido.getStatus(),
                pedido.getCanal(),
                pedido.getIdentificadorAtendimento(),
                pedido.getSubtotal(),
                pedido.getDesconto(),
                pedido.getTotal(),
                totalPago,
                saldoRestante,
                pedido.getStatusDesconto(),
                pedido.getTipoDesconto(),
                pedido.getValorDescontoInformado(),
                pedido.getDescontoSolicitado(),
                quantidadeItens,
                pedido.getCriadoEm(),
                pedido.getAtualizadoEm(),
                pedido.getConcluidoEm(),
                pedido.getCanceladoEm(),
                pedido.getEstornadoEm(),
                notaFiscal
        );
    }

    public static PedidoCaixaDetalheResponseDto paraDetalhe(
            PedidoEntity pedido,
            String operadorNome,
            String descontoDecididoPorNome,
            List<ItemPedidoEntity> itens,
            List<PagamentoPedidoEntity> pagamentos
    ) {
        return paraDetalhe(pedido, operadorNome, descontoDecididoPorNome, itens, pagamentos, null);
    }

    public static PedidoCaixaDetalheResponseDto paraDetalhe(
            PedidoEntity pedido,
            String operadorNome,
            String descontoDecididoPorNome,
            List<ItemPedidoEntity> itens,
            List<PagamentoPedidoEntity> pagamentos,
            NotaFiscalPedidoVinculadaDto notaFiscal
    ) {
        BigDecimal totalPago = calcularTotalPagoAprovado(pagamentos);
        BigDecimal saldoRestante = calcularSaldoRestante(pedido.getTotal(), totalPago);
        List<ItemPedidoCaixaResponseDto> itensDto = itens.stream()
                .map(PedidoCaixaMapper::paraItemResposta)
                .toList();
        List<PagamentoPedidoResponseDto> pagamentosDto = pagamentos.stream()
                .map(PagamentoPedidoMapper::paraResposta)
                .toList();

        return new PedidoCaixaDetalheResponseDto(
                pedido.getId(),
                pedido.getNumero(),
                pedido.getSessaoCaixaId(),
                pedido.getOperadorUsuarioId(),
                operadorNome,
                pedido.getStatus(),
                pedido.getCanal(),
                pedido.getIdentificadorAtendimento(),
                pedido.getObservacao(),
                pedido.getSubtotal(),
                pedido.getDesconto(),
                pedido.getTotal(),
                totalPago,
                saldoRestante,
                pedido.getStatusDesconto(),
                pedido.getTipoDesconto(),
                pedido.getValorDescontoInformado(),
                pedido.getDescontoSolicitado(),
                pedido.getMotivoDesconto(),
                pedido.getDescontoDecididoPorUsuarioId(),
                descontoDecididoPorNome,
                pedido.getDescontoDecididoEm(),
                pedido.getObservacaoDecisaoDesconto(),
                pedido.getMotivoCancelamento(),
                pedido.getCanceladoPorUsuarioId(),
                pedido.getMotivoEstorno(),
                pedido.getEstornadoPorUsuarioId(),
                pedido.getCriadoEm(),
                pedido.getAtualizadoEm(),
                pedido.getConcluidoEm(),
                pedido.getCanceladoEm(),
                pedido.getEstornadoEm(),
                itensDto,
                pagamentosDto,
                notaFiscal
        );
    }

    public static BigDecimal calcularTotalPagoAprovado(List<PagamentoPedidoEntity> pagamentos) {
        if (pagamentos == null || pagamentos.isEmpty()) {
            return ZERO;
        }
        return pagamentos.stream()
                .filter(PagamentoPedidoEntity::estaAprovado)
                .map(PagamentoPedidoEntity::getValor)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calcularSaldoRestante(BigDecimal totalPedido, BigDecimal totalPagoAprovado) {
        if (totalPedido == null) {
            return ZERO;
        }
        BigDecimal pago = totalPagoAprovado == null ? ZERO : totalPagoAprovado;
        BigDecimal restante = totalPedido.subtract(pago);
        return restante.compareTo(ZERO) < 0 ? ZERO : restante.setScale(2, RoundingMode.HALF_UP);
    }
}
