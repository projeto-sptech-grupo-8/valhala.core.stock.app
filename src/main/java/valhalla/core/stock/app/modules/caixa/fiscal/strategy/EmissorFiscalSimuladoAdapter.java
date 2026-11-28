package valhalla.core.stock.app.modules.caixa.fiscal.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.caixa.configuracao.enums.FormaPagamentoCaixa;
import valhalla.core.stock.app.modules.caixa.fiscal.entity.NotaFiscalEntity;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.AmbienteFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.StatusNotaFiscal;
import valhalla.core.stock.app.modules.caixa.pedido.entity.PagamentoPedidoEntity;
import valhalla.core.stock.app.modules.estoque.entity.ProdutoTipo;
import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EmissorFiscalSimuladoAdapter implements ProvedorEmissaoFiscal {

    public static final String CODIGO_PROVEDOR = "INTERNO";

    private static final String CUF_SP = "35";
    private static final String MODELO_NFCE = "65";
    private static final String REGIME_SIMPLES_NACIONAL = "SIMPLES_NACIONAL";
    private static final String CRT_SIMPLES_NACIONAL = "1";
    private static final String ORIGEM_NACIONAL = "0";

    // Tributação simplificada padrão para bebida de revenda (Substituição Tributária já recolhida)
    private static final String CFOP_REVENDA_ST = "5405";
    private static final String CSOSN_REVENDA_ST = "500";
    private static final String NCM_BEBIDA_PADRAO = "22029900";

    // Tributação simplificada padrão para drink preparado no estabelecimento
    private static final String CFOP_PREPARO_PROPRIO = "5102";
    private static final String CSOSN_TRIBUTADA_SIMPLES = "102";
    private static final String NCM_DRINK_PADRAO = "22089000";

    private static final DateTimeFormatter FORMATO_AAMM = DateTimeFormatter.ofPattern("yyMM");
    private static final DateTimeFormatter FORMATO_DATA_HORA_SEFAZ =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'-03:00'");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String codigoProvedor() {
        return CODIGO_PROVEDOR;
    }

    @Override
    public ResultadoEmissaoFiscal emitirNfce(SolicitacaoEmissaoFiscal solicitacao) {
        LocalDateTime emissao = solicitacao.dataEmissao() != null
                ? solicitacao.dataEmissao()
                : FusoHorarioOperacao.agora();

        String serie = normalizarSerie(solicitacao.serie());
        long numeroSeq = solicitacao.numeroSequencial();
        String numeroFormatado = String.valueOf(numeroSeq);

        String cnpjEmitente = normalizarCnpj(solicitacao.estabelecimento().getCnpj());
        String codigoNumerico = gerarCodigoNumerico(solicitacao.pedido().getId(), numeroSeq);
        String chaveAcesso = gerarChaveAcesso44(
                CUF_SP,
                emissao,
                cnpjEmitente,
                MODELO_NFCE,
                serie,
                numeroSeq,
                codigoNumerico
        );

        String protocolo = gerarProtocoloAutorizacao(emissao, numeroSeq);
        String urlConsulta = "https://www.homologacao.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p="
                + chaveAcesso + "|2|2|1";

        List<ItemTributadoFiscal> itensTributados = new ArrayList<>();
        for (ItemEmissaoFiscal item : solicitacao.itens()) {
            boolean isDrink = item.tipoProduto() == ProdutoTipo.DRINK;
            String cfop = isDrink ? CFOP_PREPARO_PROPRIO : CFOP_REVENDA_ST;
            String csosn = isDrink ? CSOSN_TRIBUTADA_SIMPLES : CSOSN_REVENDA_ST;
            String ncm = isDrink ? NCM_DRINK_PADRAO : NCM_BEBIDA_PADRAO;

            Map<String, Object> tributosMap = new LinkedHashMap<>();
            tributosMap.put("regime", REGIME_SIMPLES_NACIONAL);
            tributosMap.put("crt", CRT_SIMPLES_NACIONAL);
            tributosMap.put("origem", ORIGEM_NACIONAL);
            tributosMap.put("cfop", cfop);
            tributosMap.put("csosn", csosn);
            tributosMap.put("ncm", ncm);

            itensTributados.add(new ItemTributadoFiscal(
                    item.itemPedido().getId(),
                    cfop,
                    ncm,
                    csosn,
                    ORIGEM_NACIONAL,
                    objectMapper.valueToTree(tributosMap)
            ));
        }

        String xml = montarXmlNfce(
                solicitacao,
                emissao,
                cnpjEmitente,
                serie,
                numeroSeq,
                codigoNumerico,
                chaveAcesso,
                protocolo,
                urlConsulta,
                itensTributados
        );

        Map<String, Object> dadosFiscaisMap = new LinkedHashMap<>();
        dadosFiscaisMap.put("modelo", MODELO_NFCE);
        dadosFiscaisMap.put("naturezaOperacao", "VENDA AO CONSUMIDOR");
        dadosFiscaisMap.put("regimeTributario", REGIME_SIMPLES_NACIONAL);
        dadosFiscaisMap.put("crt", CRT_SIMPLES_NACIONAL);
        dadosFiscaisMap.put("cUF", CUF_SP);
        dadosFiscaisMap.put("codigoStatusSefaz", "100");
        dadosFiscaisMap.put("motivoSefaz", "Autorizado o uso da NFC-e");
        dadosFiscaisMap.put("protocoloAutorizacao", protocolo);
        dadosFiscaisMap.put("urlConsultaQrCode", urlConsulta);

        return new ResultadoEmissaoFiscal(
                CODIGO_PROVEDOR,
                AmbienteFiscal.HOMOLOGACAO,
                StatusNotaFiscal.AUTORIZADA,
                numeroFormatado,
                serie,
                chaveAcesso,
                protocolo,
                urlConsulta,
                objectMapper.valueToTree(dadosFiscaisMap),
                xml,
                itensTributados
        );
    }

    @Override
    public ResultadoCancelamentoFiscal cancelarNfce(NotaFiscalEntity notaFiscal, String motivo) {
        LocalDateTime canceladaEm = FusoHorarioOperacao.agora();
        long sufixo = Math.abs(notaFiscal.getId() != null ? notaFiscal.getId().hashCode() : 1L) % 1_000_000_000L;
        String protocoloCancelamento = "135"
                + String.valueOf(canceladaEm.getYear()).substring(2)
                + String.format("%010d", sufixo);

        ObjectNode dadosAtualizados = notaFiscal.getDadosFiscais() != null && notaFiscal.getDadosFiscais().isObject()
                ? (ObjectNode) notaFiscal.getDadosFiscais().deepCopy()
                : objectMapper.createObjectNode();

        dadosAtualizados.put("codigoStatusCancelamentoSefaz", "135");
        dadosAtualizados.put("motivoCancelamentoSefaz", "Evento registrado e vinculado a NFC-e");
        dadosAtualizados.put("protocoloCancelamento", protocoloCancelamento);
        dadosAtualizados.put("justificativaCancelamento", motivo);
        dadosAtualizados.put("canceladaEm", canceladaEm.format(FORMATO_DATA_HORA_SEFAZ));

        return new ResultadoCancelamentoFiscal(
                StatusNotaFiscal.CANCELADA,
                protocoloCancelamento,
                canceladaEm,
                dadosAtualizados
        );
    }

    public static String gerarChaveAcesso44(
            String cUf,
            LocalDateTime emissao,
            String cnpj14,
            String modelo2,
            String serie,
            long numeroSequencial,
            String codigoNumerico8
    ) {
        String aamm = emissao.format(FORMATO_AAMM);
        String serie3 = String.format("%03d", Integer.parseInt(serie));
        String numero9 = String.format("%09d", numeroSequencial);
        String tpEmis = "1";

        String base43 = cUf + aamm + cnpj14 + modelo2 + serie3 + numero9 + tpEmis + codigoNumerico8;
        int dv = calcularDigitoVerificadorModulo11(base43);
        return base43 + dv;
    }

    public static int calcularDigitoVerificadorModulo11(String base43) {
        int peso = 2;
        int soma = 0;
        for (int i = base43.length() - 1; i >= 0; i--) {
            int digito = Character.digit(base43.charAt(i), 10);
            soma += digito * peso;
            peso = (peso == 9) ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return (resto < 2) ? 0 : (11 - resto);
    }

    private String montarXmlNfce(
            SolicitacaoEmissaoFiscal solicitacao,
            LocalDateTime emissao,
            String cnpjEmitente,
            String serie,
            long numeroSeq,
            String codigoNumerico,
            String chaveAcesso,
            String protocolo,
            String urlConsulta,
            List<ItemTributadoFiscal> itensTributados
    ) {
        String dhEmi = emissao.format(FORMATO_DATA_HORA_SEFAZ);
        char cDv = chaveAcesso.charAt(43);

        StringBuilder detBuilder = new StringBuilder();
        List<ItemEmissaoFiscal> itens = solicitacao.itens();
        for (int i = 0; i < itens.size(); i++) {
            ItemEmissaoFiscal item = itens.get(i);
            ItemTributadoFiscal trib = itensTributados.get(i);
            int nItem = i + 1;
            String codigoProduto = item.sku() != null && !item.sku().isBlank()
                    ? item.sku()
                    : String.valueOf(nItem);
            String unidade = (item.unidadeMedida() == null || item.unidadeMedida().isBlank())
                    ? "UN"
                    : item.unidadeMedida().trim().toUpperCase();

            detBuilder.append("<det nItem=\"").append(nItem).append("\">")
                    .append("<prod>")
                    .append("<cProd>").append(escaparXml(codigoProduto)).append("</cProd>")
                    .append("<cEAN>SEM GTIN</cEAN>")
                    .append("<xProd>").append(escaparXml(item.itemPedido().getDescricaoProduto())).append("</xProd>")
                    .append("<NCM>").append(trib.ncm()).append("</NCM>")
                    .append("<CFOP>").append(trib.cfop()).append("</CFOP>")
                    .append("<uCom>").append(escaparXml(unidade)).append("</uCom>")
                    .append("<qCom>").append(formatarDecimal(item.itemPedido().getQuantidade(), 4)).append("</qCom>")
                    .append("<vUnCom>").append(formatarDecimal(item.itemPedido().getPrecoUnitario(), 2)).append("</vUnCom>")
                    .append("<vProd>").append(formatarDecimal(item.valorBruto(), 2)).append("</vProd>")
                    .append("<cEANTrib>SEM GTIN</cEANTrib>")
                    .append("<uTrib>").append(escaparXml(unidade)).append("</uTrib>")
                    .append("<qTrib>").append(formatarDecimal(item.itemPedido().getQuantidade(), 4)).append("</qTrib>")
                    .append("<vUnTrib>").append(formatarDecimal(item.itemPedido().getPrecoUnitario(), 2)).append("</vUnTrib>");

            if (item.valorDesconto() != null && item.valorDesconto().compareTo(BigDecimal.ZERO) > 0) {
                detBuilder.append("<vDesc>").append(formatarDecimal(item.valorDesconto(), 2)).append("</vDesc>");
            }

            detBuilder.append("<indTot>1</indTot>")
                    .append("</prod>")
                    .append("<imposto><ICMS>");

            if (CSOSN_REVENDA_ST.equals(trib.csosn())) {
                detBuilder.append("<ICMSSN500>")
                        .append("<orig>").append(trib.origem()).append("</orig>")
                        .append("<CSOSN>500</CSOSN>")
                        .append("</ICMSSN500>");
            } else {
                detBuilder.append("<ICMSSN102>")
                        .append("<orig>").append(trib.origem()).append("</orig>")
                        .append("<CSOSN>102</CSOSN>")
                        .append("</ICMSSN102>");
            }

            detBuilder.append("</ICMS></imposto>")
                    .append("</det>");
        }

        StringBuilder destBuilder = new StringBuilder();
        if (solicitacao.contraparteDocumento() != null && !solicitacao.contraparteDocumento().isBlank()) {
            String doc = solicitacao.contraparteDocumento();
            destBuilder.append("<dest>");
            if (doc.length() == 11) {
                destBuilder.append("<CPF>").append(doc).append("</CPF>");
            } else {
                destBuilder.append("<CNPJ>").append(doc).append("</CNPJ>");
            }
            if (solicitacao.contraparteNome() != null && !solicitacao.contraparteNome().isBlank()) {
                destBuilder.append("<xNome>").append(escaparXml(solicitacao.contraparteNome())).append("</xNome>");
            }
            destBuilder.append("<indIEDest>9</indIEDest>")
                    .append("</dest>");
        }

        StringBuilder pagBuilder = new StringBuilder("<pag>");
        BigDecimal trocoTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (PagamentoPedidoEntity pag : solicitacao.pagamentosAprovados()) {
            String tPag = mapearCodigoPagamentoSefaz(pag.getForma());
            BigDecimal valorPag = pag.getValorRecebido() != null
                    ? pag.getValorRecebido()
                    : pag.getValor();
            pagBuilder.append("<detPag>")
                    .append("<tPag>").append(tPag).append("</tPag>")
                    .append("<vPag>").append(formatarDecimal(valorPag, 2)).append("</vPag>")
                    .append("</detPag>");
            if (pag.getValorTroco() != null && pag.getValorTroco().compareTo(BigDecimal.ZERO) > 0) {
                trocoTotal = trocoTotal.add(pag.getValorTroco()).setScale(2, RoundingMode.HALF_UP);
            }
        }
        if (trocoTotal.compareTo(BigDecimal.ZERO) > 0) {
            pagBuilder.append("<vTroco>").append(formatarDecimal(trocoTotal, 2)).append("</vTroco>");
        }
        pagBuilder.append("</pag>");

        String razaoSocial = solicitacao.estabelecimento().getCorporateName();
        String nomeFantasia = solicitacao.estabelecimento().getTradeName();

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"4.00\">"
                + "<NFe>"
                + "<infNFe Id=\"NFe" + chaveAcesso + "\" versao=\"4.00\">"
                + "<ide>"
                + "<cUF>" + CUF_SP + "</cUF>"
                + "<cNF>" + codigoNumerico + "</cNF>"
                + "<natOp>VENDA AO CONSUMIDOR</natOp>"
                + "<mod>" + MODELO_NFCE + "</mod>"
                + "<serie>" + Integer.parseInt(serie) + "</serie>"
                + "<nNF>" + numeroSeq + "</nNF>"
                + "<dhEmi>" + dhEmi + "</dhEmi>"
                + "<tpNF>1</tpNF>"
                + "<idDest>1</idDest>"
                + "<tpImp>4</tpImp>"
                + "<tpEmis>1</tpEmis>"
                + "<cDV>" + cDv + "</cDV>"
                + "<tpAmb>2</tpAmb>"
                + "<finNFe>1</finNFe>"
                + "<indFinal>1</indFinal>"
                + "<indPres>1</indPres>"
                + "</ide>"
                + "<emit>"
                + "<CNPJ>" + cnpjEmitente + "</CNPJ>"
                + "<xNome>" + escaparXml(razaoSocial) + "</xNome>"
                + "<xFant>" + escaparXml(nomeFantasia) + "</xFant>"
                + "<CRT>" + CRT_SIMPLES_NACIONAL + "</CRT>"
                + "</emit>"
                + destBuilder
                + detBuilder
                + "<total><ICMSTot>"
                + "<vBC>0.00</vBC>"
                + "<vICMS>0.00</vICMS>"
                + "<vProd>" + formatarDecimal(solicitacao.pedido().getSubtotal(), 2) + "</vProd>"
                + "<vDesc>" + formatarDecimal(solicitacao.pedido().getDesconto(), 2) + "</vDesc>"
                + "<vNF>" + formatarDecimal(solicitacao.pedido().getTotal(), 2) + "</vNF>"
                + "</ICMSTot></total>"
                + "<transp><modFrete>9</modFrete></transp>"
                + pagBuilder
                + "</infNFe>"
                + "<infNFeSupl>"
                + "<qrCode>" + escaparXml(urlConsulta) + "</qrCode>"
                + "</infNFeSupl>"
                + "</NFe>"
                + "<protNFe versao=\"4.00\">"
                + "<infProt>"
                + "<tpAmb>2</tpAmb>"
                + "<chNFe>" + chaveAcesso + "</chNFe>"
                + "<dhRecbto>" + dhEmi + "</dhRecbto>"
                + "<nProt>" + protocolo + "</nProt>"
                + "<cStat>100</cStat>"
                + "<xMotivo>Autorizado o uso da NFC-e</xMotivo>"
                + "</infProt>"
                + "</protNFe>"
                + "</nfeProc>";
    }

    public static String mapearCodigoPagamentoSefaz(FormaPagamentoCaixa forma) {
        if (forma == null) {
            return "99";
        }
        return switch (forma) {
            case DINHEIRO -> "01";
            case CREDITO -> "03";
            case DEBITO -> "04";
            case PIX -> "17";
        };
    }

    private String normalizarSerie(String serie) {
        if (serie == null || serie.isBlank()) {
            return "1";
        }
        String digitos = serie.replaceAll("\\D", "");
        if (digitos.isEmpty()) {
            return "1";
        }
        int valor = Integer.parseInt(digitos);
        return valor <= 0 ? "1" : String.valueOf(valor);
    }

    private String normalizarCnpj(String cnpj) {
        String digitos = cnpj == null ? "" : cnpj.replaceAll("\\D", "");
        if (digitos.length() >= 14) {
            return digitos.substring(0, 14);
        }
        return String.format("%14s", digitos).replace(' ', '0');
    }

    private String gerarCodigoNumerico(UUID pedidoId, long numeroSeq) {
        long hash = pedidoId != null ? Math.abs((long) pedidoId.hashCode()) : 12345678L;
        long codigo = (hash * 31L + numeroSeq * 17L) % 100_000_000L;
        if (codigo == numeroSeq || codigo == 0L) {
            codigo = (codigo + 13579113L) % 100_000_000L;
        }
        return String.format("%08d", codigo);
    }

    private String gerarProtocoloAutorizacao(LocalDateTime emissao, long numeroSeq) {
        String ano2 = String.valueOf(emissao.getYear()).substring(2);
        return "135" + ano2 + String.format("%010d", numeroSeq);
    }

    private String formatarDecimal(BigDecimal valor, int casas) {
        BigDecimal seguro = valor == null ? BigDecimal.ZERO : valor;
        return seguro.setScale(casas, RoundingMode.HALF_UP).toPlainString();
    }

    private String escaparXml(String texto) {
        if (texto == null) {
            return "";
        }
        return texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
