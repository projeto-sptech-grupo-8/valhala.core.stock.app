package valhalla.core.stock.app.modules.caixa.fiscal.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import valhalla.core.stock.app.modules.caixa.fiscal.entity.NotaFiscalEntity;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.AmbienteFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.StatusNotaFiscal;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface ProvedorEmissaoFiscal {

    String codigoProvedor();

    ResultadoEmissaoFiscal emitirNfce(SolicitacaoEmissaoFiscal solicitacao);

    ResultadoCancelamentoFiscal cancelarNfce(NotaFiscalEntity notaFiscal, String motivo);

    record SolicitacaoEmissaoFiscal(
            EstablishmentEntity estabelecimento,
            PedidoEntity pedido,
            Long numeroSequencial,
            String serie,
            String contraparteDocumento,
            String contraparteNome,
            LocalDateTime dataEmissao,
            List<ItemEmissaoFiscal> itens,
            List<PagamentoPedidoEntity> pagamentosAprovados
    ) {
    }

    record ItemEmissaoFiscal(
            ItemPedidoEntity itemPedido,
            ProdutoTipo tipoProduto,
            String sku,
            String unidadeMedida,
            BigDecimal valorBruto,
            BigDecimal valorDesconto,
            BigDecimal valorLiquido
    ) {
    }

    record ItemTributadoFiscal(
            Long itemPedidoId,
            String cfop,
            String ncm,
            String csosn,
            String origem,
            JsonNode dadosTributarios
    ) {
    }

    record ResultadoEmissaoFiscal(
            String provedor,
            AmbienteFiscal ambiente,
            StatusNotaFiscal status,
            String numero,
            String serie,
            String chaveAcesso,
            String protocoloAutorizacao,
            String urlConsulta,
            JsonNode dadosFiscais,
            String xmlConteudo,
            List<ItemTributadoFiscal> itensTributados
    ) {
    }

    record ResultadoCancelamentoFiscal(
            StatusNotaFiscal status,
            String protocoloCancelamento,
            LocalDateTime canceladaEm,
            JsonNode dadosFiscaisAtualizados
    ) {
    }
}
