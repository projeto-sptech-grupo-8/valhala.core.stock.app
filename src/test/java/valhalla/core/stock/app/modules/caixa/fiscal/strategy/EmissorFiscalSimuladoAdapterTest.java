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

    @Test
    void cobreMapeamentosPagamentoSerieNulaCnpjCurtoSemDestinatarioECancelamentoComDadosExistentes() {
        assertThat(adapter.codigoProvedor()).isEqualTo("INTERNO");
        assertThat(EmissorFiscalSimuladoAdapter.mapearCodigoPagamentoSefaz(FormaPagamentoCaixa.DINHEIRO)).isEqualTo("01");
        assertThat(EmissorFiscalSimuladoAdapter.mapearCodigoPagamentoSefaz(FormaPagamentoCaixa.CREDITO)).isEqualTo("03");
        assertThat(EmissorFiscalSimuladoAdapter.mapearCodigoPagamentoSefaz(FormaPagamentoCaixa.DEBITO)).isEqualTo("04");
        assertThat(EmissorFiscalSimuladoAdapter.mapearCodigoPagamentoSefaz(FormaPagamentoCaixa.PIX)).isEqualTo("17");
        assertThat(EmissorFiscalSimuladoAdapter.mapearCodigoPagamentoSefaz(null)).isEqualTo("99");

        EstablishmentEntity estabelecimento = EstablishmentEntity.builder()
                .id(UUID.randomUUID())
                .corporateName("Adega <Teste> \"A\" 'B'")
                .tradeName(null)
                .cnpj("123")
                .build();

        PedidoEntity pedido = new PedidoEntity();
        pedido.setId(UUID.randomUUID());
        pedido.setEstabelecimentoId(estabelecimento.getId());
        pedido.setNumero(9L);
        pedido.setStatus(StatusPedidoCaixa.CONCLUIDO);
        pedido.setSubtotal(new BigDecimal("25.00"));
        pedido.setDesconto(BigDecimal.ZERO);
        pedido.setTotal(new BigDecimal("25.00"));

        ItemPedidoEntity item = new ItemPedidoEntity();
        item.setId(1L);
        item.setPedidoId(pedido.getId());
        item.setProdutoId(UUID.randomUUID());
        item.setDescricaoProduto("Água Mineral");
        item.setQuantidade(BigDecimal.ONE);
        item.setPrecoUnitario(new BigDecimal("25.00"));
        item.setSubtotal(new BigDecimal("25.00"));

        PagamentoPedidoEntity pagPix = new PagamentoPedidoEntity();
        pagPix.setId(UUID.randomUUID());
        pagPix.setPedidoId(pedido.getId());
        pagPix.setForma(FormaPagamentoCaixa.PIX);
        pagPix.setStatus(StatusPagamentoPedido.APROVADO);
        pagPix.setValor(new BigDecimal("25.00"));
        pagPix.setValorRecebido(null);
        pagPix.setValorTroco(null);

        // Sem dataEmissao, serie invalida, sem sku/unidade, sem desconto, sem contraparteDocumento -> fallback serie "1"
        ResultadoEmissaoFiscal semDest = adapter.emitirNfce(new SolicitacaoEmissaoFiscal(
                estabelecimento, pedido, 1L, "abc", null, null, null,
                List.of(new ItemEmissaoFiscal(
                        item, ProdutoTipo.PADRAO, " ", " ",
                        new BigDecimal("25.00"), BigDecimal.ZERO, new BigDecimal("25.00")
                )),
                List.of(pagPix)
        ));
        assertThat(semDest.serie()).isEqualTo("1");
        assertThat(semDest.xmlConteudo())
                .doesNotContain("<dest>")
                .doesNotContain("<vDesc>0.00</vDesc></prod>")
                .doesNotContain("<vTroco>")
                .contains("<tPag>17</tPag>")
                .contains("<CNPJ>00000000000123</CNPJ>")
                .contains("&lt;Teste&gt; &quot;A&quot; &apos;B&apos;");

        // serie "0" -> "1"; serie "002" -> "2"; contraparte CNPJ com nome em branco
        ResultadoEmissaoFiscal serieZero = adapter.emitirNfce(new SolicitacaoEmissaoFiscal(
                estabelecimento, pedido, 2L, "0", "12345678000199", "   ", null,
                List.of(new ItemEmissaoFiscal(
                        item, ProdutoTipo.PADRAO, "SKU-X", "UN",
                        new BigDecimal("25.00"), null, new BigDecimal("25.00")
                )),
                List.of(pagPix)
        ));
        assertThat(serieZero.serie()).isEqualTo("1");
        assertThat(serieZero.xmlConteudo()).contains("<CNPJ>12345678000199</CNPJ>").doesNotContain("<xNome></xNome>");

        ResultadoEmissaoFiscal serieDois = adapter.emitirNfce(new SolicitacaoEmissaoFiscal(
                estabelecimento, pedido, 3L, "002", null, null, LocalDateTime.of(2026, 11, 19, 10, 0),
                List.of(new ItemEmissaoFiscal(
                        item, ProdutoTipo.PADRAO, null, null,
                        new BigDecimal("25.00"), BigDecimal.ZERO, new BigDecimal("25.00")
                )),
                List.of(pagPix)
        ));
        assertThat(serieDois.serie()).isEqualTo("2");

        // cancelarNfce com nota.id nulo e dadosFiscais existente preservando chaves prévias
        NotaFiscalEntity notaExistente = new NotaFiscalEntity();
        notaExistente.setId(null);
        notaExistente.setDadosFiscais(
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("codigoStatusSefaz", "100")
        );
        ResultadoCancelamentoFiscal canc = adapter.cancelarNfce(notaExistente, "Cancelamento com metadado preservado");
        assertThat(canc.dadosFiscaisAtualizados().get("codigoStatusSefaz").asText()).isEqualTo("100");
        assertThat(canc.dadosFiscaisAtualizados().get("codigoStatusCancelamentoSefaz").asText()).isEqualTo("135");
    }
}
