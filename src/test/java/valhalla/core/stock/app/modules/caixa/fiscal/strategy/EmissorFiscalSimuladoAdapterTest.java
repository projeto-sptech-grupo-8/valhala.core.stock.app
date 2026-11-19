package valhalla.core.stock.app.modules.caixa.fiscal.strategy;

import org.junit.jupiter.api.Test;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.fiscal.entity.NotaFiscalEntity;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.AmbienteFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.StatusNotaFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal.ItemEmissaoFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal.ResultadoCancelamentoFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal.ResultadoEmissaoFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.strategy.ProvedorEmissaoFiscal.SolicitacaoEmissaoFiscal;
import valhalla.core.stock.app.modules.caixa.pedido.entity.ItemPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PedidoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPagamentoPedido;
import valhalla.core.stock.app.modules.caixa.pedido.enums.StatusPedidoCaixa;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmissorFiscalSimuladoAdapterTest {

    private final EmissorFiscalSimuladoAdapter adapter = new EmissorFiscalSimuladoAdapter();

    @Test
    void geraChaveAcesso44DigitosValidaXml400ETributacaoAutomaticaSimplesNacional() {
        EstablishmentEntity estabelecimento = EstablishmentEntity.builder()
                .id(UUID.randomUUID())
                .corporateName("Adega Meraki & Cia LTDA")
                .tradeName("Adega Meraki")
                .cnpj("12345678000199")
                .build();

        PedidoEntity pedido = new PedidoEntity();
        pedido.setId(UUID.randomUUID());
        pedido.setEstabelecimentoId(estabelecimento.getId());
        pedido.setNumero(42L);
        pedido.setStatus(StatusPedidoCaixa.CONCLUIDO);
        pedido.setSubtotal(new BigDecimal("68.00"));
        pedido.setDesconto(new BigDecimal("8.00"));
        pedido.setTotal(new BigDecimal("60.00"));

        ItemPedidoEntity itemBebida = new ItemPedidoEntity();
        itemBebida.setId(10L);
        itemBebida.setPedidoId(pedido.getId());
        itemBebida.setProdutoId(UUID.randomUUID());
        itemBebida.setDescricaoProduto("Cerveja Heineken 600ml");
        itemBebida.setQuantidade(new BigDecimal("2.000"));
        itemBebida.setPrecoUnitario(new BigDecimal("18.00"));
        itemBebida.setSubtotal(new BigDecimal("36.00"));

        ItemPedidoEntity itemDrink = new ItemPedidoEntity();
        itemDrink.setId(11L);
        itemDrink.setPedidoId(pedido.getId());
        itemDrink.setProdutoId(UUID.randomUUID());
        itemDrink.setDescricaoProduto("Gin & Tônica Meraki");
        itemDrink.setQuantidade(new BigDecimal("1.000"));
        itemDrink.setPrecoUnitario(new BigDecimal("32.00"));
        itemDrink.setSubtotal(new BigDecimal("32.00"));

        List<ItemEmissaoFiscal> itens = List.of(
                new ItemEmissaoFiscal(
                        itemBebida,
                        ProdutoTipo.PADRAO,
                        "SKU-HNK600",
                        "UN",
                        new BigDecimal("36.00"),
                        new BigDecimal("4.24"),
                        new BigDecimal("31.76")
                ),
                new ItemEmissaoFiscal(
                        itemDrink,
                        ProdutoTipo.DRINK,
                        "DRK-GT01",
                        "UN",
                        new BigDecimal("32.00"),
                        new BigDecimal("3.76"),
                        new BigDecimal("28.24")
                )
        );

        PagamentoPedidoEntity pagDinheiro = new PagamentoPedidoEntity();
        pagDinheiro.setId(UUID.randomUUID());
        pagDinheiro.setPedidoId(pedido.getId());
        pagDinheiro.setForma(FormaPagamentoCaixa.DINHEIRO);
        pagDinheiro.setStatus(StatusPagamentoPedido.APROVADO);
        pagDinheiro.setValor(new BigDecimal("60.00"));
        pagDinheiro.setValorRecebido(new BigDecimal("100.00"));
        pagDinheiro.setValorTroco(new BigDecimal("40.00"));

        LocalDateTime dataEmissao = LocalDateTime.of(2026, 11, 19, 10, 30, 0);

        SolicitacaoEmissaoFiscal solicitacao = new SolicitacaoEmissaoFiscal(
                estabelecimento,
                pedido,
                1L,
                "1",
                "12345678909",
                "Lucas Peres",
                dataEmissao,
                itens,
                List.of(pagDinheiro)
        );

        ResultadoEmissaoFiscal resultado = adapter.emitirNfce(solicitacao);

        assertThat(resultado.provedor()).isEqualTo("INTERNO");
        assertThat(resultado.ambiente()).isEqualTo(AmbienteFiscal.HOMOLOGACAO);
        assertThat(resultado.status()).isEqualTo(StatusNotaFiscal.AUTORIZADA);
        assertThat(resultado.numero()).isEqualTo("1");
        assertThat(resultado.serie()).isEqualTo("1");
        assertThat(resultado.chaveAcesso()).hasSize(44).containsOnlyDigits();

        // Verifica o dígito verificador Módulo 11 da chave de 44 dígitos
        String base43 = resultado.chaveAcesso().substring(0, 43);
        int dvCalculado = EmissorFiscalSimuladoAdapter.calcularDigitoVerificadorModulo11(base43);
        assertThat(Character.digit(resultado.chaveAcesso().charAt(43), 10)).isEqualTo(dvCalculado);

        // Tributação automática Simples Nacional: bebida revenda (5405/500) vs drink (5102/102)
        assertThat(resultado.itensTributados()).hasSize(2);
        assertThat(resultado.itensTributados().get(0).cfop()).isEqualTo("5405");
        assertThat(resultado.itensTributados().get(0).csosn()).isEqualTo("500");
        assertThat(resultado.itensTributados().get(1).cfop()).isEqualTo("5102");
        assertThat(resultado.itensTributados().get(1).csosn()).isEqualTo("102");

        // Verifica estrutura do XML 4.00
        assertThat(resultado.xmlConteudo())
                .contains("<mod>65</mod>")
                .contains("<CNPJ>12345678000199</CNPJ>")
                .contains("<xNome>Adega Meraki &amp; Cia LTDA</xNome>")
                .contains("<CPF>12345678909</CPF>")
                .contains("<xProd>Gin &amp; Tônica Meraki</xProd>")
                .contains("<CSOSN>500</CSOSN>")
                .contains("<CSOSN>102</CSOSN>")
                .contains("<vProd>68.00</vProd>")
                .contains("<vDesc>8.00</vDesc>")
                .contains("<vNF>60.00</vNF>")
                .contains("<tPag>01</tPag>")
                .contains("<vPag>100.00</vPag>")
                .contains("<vTroco>40.00</vTroco>")
                .contains("<cStat>100</cStat>");
    }

    @Test
    void registraEventoDeCancelamentoNosDadosFiscais() {
        NotaFiscalEntity nota = new NotaFiscalEntity();
        nota.setId(UUID.randomUUID());
        nota.setStatus(StatusNotaFiscal.AUTORIZADA);

        ResultadoCancelamentoFiscal cancelamento = adapter.cancelarNfce(
                nota,
                "Cancelamento solicitado por erro no CPF do consumidor"
        );

        assertThat(cancelamento.status()).isEqualTo(StatusNotaFiscal.CANCELADA);
        assertThat(cancelamento.protocoloCancelamento()).isNotBlank();
        assertThat(cancelamento.canceladaEm()).isNotNull();
        assertThat(cancelamento.dadosFiscaisAtualizados().get("codigoStatusCancelamentoSefaz").asText())
                .isEqualTo("135");
        assertThat(cancelamento.dadosFiscaisAtualizados().get("justificativaCancelamento").asText())
                .isEqualTo("Cancelamento solicitado por erro no CPF do consumidor");
    }
}
