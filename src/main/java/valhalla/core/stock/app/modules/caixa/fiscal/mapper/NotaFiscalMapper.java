package valhalla.core.stock.app.modules.caixa.fiscal.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.EmitenteNotaFiscalResponseDto;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.ItemNotaFiscalResponseDto;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.NotaFiscalDetalheResponseDto;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.NotaFiscalPedidoVinculadaDto;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.NotaFiscalResumoResponseDto;
import valhalla.core.stock.app.modules.caixa.fiscal.dto.PagamentoNotaFiscalResponseDto;
import valhalla.core.stock.app.modules.caixa.fiscal.entity.ItemNotaFiscalEntity;
import valhalla.core.stock.app.modules.caixa.fiscal.entity.NotaFiscalEntity;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.EmissorFiscalSimuladoAdapter;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class NotaFiscalMapper {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private NotaFiscalMapper() {
    }

    public static NotaFiscalPedidoVinculadaDto paraVinculoPedido(NotaFiscalEntity nota) {
        if (nota == null) {
            return null;
        }
        return new NotaFiscalPedidoVinculadaDto(
                nota.getId(),
                nota.getStatus(),
                nota.getNumero(),
                nota.getSerie(),
                nota.getChaveAcesso(),
                nota.getEmitidaEm()
        );
    }

    public static NotaFiscalResumoResponseDto paraResumo(
            NotaFiscalEntity nota,
            Long pedidoNumero
    ) {
        return new NotaFiscalResumoResponseDto(
                nota.getId(),
                nota.getPedidoId(),
                pedidoNumero,
                nota.getTipo(),
                nota.getModelo(),
                nota.getAmbiente(),
                nota.getStatus(),
                nota.getNumero(),
                nota.getSerie(),
                nota.getChaveAcesso(),
                nota.getProtocoloAutorizacao(),
                nota.getContraparteNome(),
                nota.getContraparteDocumento(),
                nota.getValorProdutos(),
                nota.getValorDesconto(),
                nota.getValorTotal(),
                nota.getEmitidaEm(),
                nota.getCanceladaEm()
        );
    }

    public static ItemNotaFiscalResponseDto paraItemResposta(ItemNotaFiscalEntity item) {
        BigDecimal desconto = item.getValorDesconto() != null ? item.getValorDesconto() : ZERO;
        BigDecimal liquido = item.getValorTotal() != null ? item.getValorTotal() : ZERO;
        BigDecimal bruto = liquido.add(desconto).setScale(2, RoundingMode.HALF_UP);

        JsonNode trib = item.getDadosTributarios();
        String ncm = trib != null && trib.hasNonNull("ncm") ? trib.get("ncm").asText() : "22029900";
        String csosn = trib != null && trib.hasNonNull("csosn") ? trib.get("csosn").asText() : "500";

        return new ItemNotaFiscalResponseDto(
                item.getId(),
                item.getProdutoId(),
                item.getItemPedidoId(),
                item.getDescricaoProduto(),
                item.getUnidadeMedida(),
                item.getCfop(),
                ncm,
                csosn,
                item.getQuantidade(),
                item.getPrecoUnitario(),
                bruto,
                desconto,
                liquido
        );
    }

    public static PagamentoNotaFiscalResponseDto paraPagamentoResposta(PagamentoPedidoEntity pagamento) {
        return new PagamentoNotaFiscalResponseDto(
                pagamento.getId(),
                pagamento.getForma(),
                EmissorFiscalSimuladoAdapter.mapearCodigoPagamentoSefaz(pagamento.getForma()),
                pagamento.getValor(),
                pagamento.getValorRecebido(),
                pagamento.getValorTroco() != null ? pagamento.getValorTroco() : ZERO,
                pagamento.getParcelas(),
                pagamento.getPagoEm()
        );
    }

    public static NotaFiscalDetalheResponseDto paraDetalhe(
            NotaFiscalEntity nota,
            Long pedidoNumero,
            String emitidaPorNome,
            String canceladaPorNome,
            EstablishmentEntity estabelecimento,
            List<ItemNotaFiscalEntity> itens,
            List<PagamentoPedidoEntity> pagamentosAprovados
    ) {
        List<ItemNotaFiscalResponseDto> itensDto = itens.stream()
                .map(NotaFiscalMapper::paraItemResposta)
                .toList();

        List<PagamentoNotaFiscalResponseDto> pagamentosDto = pagamentosAprovados.stream()
                .map(NotaFiscalMapper::paraPagamentoResposta)
                .toList();

        BigDecimal valorTrocoTotal = pagamentosAprovados.stream()
                .map(p -> p.getValorTroco() != null ? p.getValorTroco() : ZERO)
                .reduce(ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        EmitenteNotaFiscalResponseDto emitenteDto = estabelecimento != null
                ? new EmitenteNotaFiscalResponseDto(
                estabelecimento.getId(),
                estabelecimento.getCorporateName(),
                estabelecimento.getTradeName(),
                estabelecimento.getCnpj()
        )
                : null;

        return new NotaFiscalDetalheResponseDto(
                nota.getId(),
                nota.getPedidoId(),
                pedidoNumero,
                nota.getTipo(),
                nota.getModelo(),
                nota.getAmbiente(),
                nota.getProvedor(),
                nota.getStatus(),
                nota.getNumero(),
                nota.getSerie(),
                nota.getChaveAcesso(),
                nota.getProtocoloAutorizacao(),
                nota.getUrlConsulta(),
                nota.getContraparteNome(),
                nota.getContraparteDocumento(),
                nota.getValorProdutos(),
                nota.getValorDesconto(),
                nota.getValorTotal(),
                valorTrocoTotal,
                nota.getEmitidaPorUsuarioId(),
                emitidaPorNome,
                nota.getEmitidaEm(),
                nota.getCanceladaPorUsuarioId(),
                canceladaPorNome,
                nota.getCanceladaEm(),
                nota.getMotivoCancelamento(),
                nota.getCriadaEm(),
                nota.getAtualizadaEm(),
                emitenteDto,
                itensDto,
                pagamentosDto
        );
    }
}
