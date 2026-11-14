package valhalla.core.stock.app.modules.caixa.configuracao.mapper;

import valhalla.core.stock.app.modules.caixa.configuracao.dto.ConfiguracaoCaixaRequestDto;
import valhalla.core.stock.app.modules.caixa.configuracao.dto.ConfiguracaoCaixaResponseDto;
import valhalla.core.stock.app.modules.caixa.configuracao.entity.ConfiguracaoCaixaEntity;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ConfiguracaoCaixaMapper {

    private ConfiguracaoCaixaMapper() {
    }

    public static ConfiguracaoCaixaEntity criarPadrao(UUID estabelecimentoId) {
        ConfiguracaoCaixaEntity configuracao = new ConfiguracaoCaixaEntity();
        configuracao.setEstabelecimentoId(estabelecimentoId);
        return configuracao;
    }

    public static void aplicar(ConfiguracaoCaixaEntity configuracao, ConfiguracaoCaixaRequestDto dto) {
        configuracao.setPointHabilitado(dto.pointHabilitado());
        configuracao.setDinheiroHabilitado(dto.dinheiroHabilitado());
        configuracao.setDebitoHabilitado(dto.debitoHabilitado());
        configuracao.setCreditoHabilitado(dto.creditoHabilitado());
        configuracao.setPixHabilitado(dto.pixHabilitado());
        configuracao.setParcelamentoHabilitado(dto.parcelamentoHabilitado());
        configuracao.setParcelasMinimas(dto.parcelasMinimas());
        configuracao.setParcelasMaximas(dto.parcelasMaximas());
        configuracao.setValorMinimoParcela(escala(dto.valorMinimoParcela()));
        configuracao.setPoliticaJuros(dto.politicaJuros());
        configuracao.setValidadeCobrancaSegundos(dto.validadeCobrancaSegundos());
        configuracao.setLimiteDescontoPercentual(escala(dto.limiteDescontoPercentual()));
        configuracao.setLimiteDescontoValor(escala(dto.limiteDescontoValor()));
        configuracao.setImprimirComprovante(dto.imprimirComprovante());
        configuracao.setPermitirPagamentoManual(dto.permitirPagamentoManual());
    }

    public static ConfiguracaoCaixaResponseDto paraResposta(ConfiguracaoCaixaEntity configuracao) {
        return new ConfiguracaoCaixaResponseDto(
                configuracao.getId(),
                configuracao.isPointHabilitado(),
                configuracao.isDinheiroHabilitado(),
                configuracao.isDebitoHabilitado(),
                configuracao.isCreditoHabilitado(),
                configuracao.isPixHabilitado(),
                configuracao.isParcelamentoHabilitado(),
                configuracao.getParcelasMinimas(),
                configuracao.getParcelasMaximas(),
                configuracao.getValorMinimoParcela(),
                configuracao.getPoliticaJuros(),
                configuracao.getValidadeCobrancaSegundos(),
                configuracao.getLimiteDescontoPercentual(),
                configuracao.getLimiteDescontoValor(),
                configuracao.isImprimirComprovante(),
                configuracao.isPermitirPagamentoManual(),
                configuracao.getAtualizadoPorUsuarioId(),
                configuracao.getCriadoEm(),
                configuracao.getAtualizadoEm()
        );
    }

    public static Map<String, Object> snapshot(ConfiguracaoCaixaEntity configuracao) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("pointHabilitado", configuracao.isPointHabilitado());
        dados.put("dinheiroHabilitado", configuracao.isDinheiroHabilitado());
        dados.put("debitoHabilitado", configuracao.isDebitoHabilitado());
        dados.put("creditoHabilitado", configuracao.isCreditoHabilitado());
        dados.put("pixHabilitado", configuracao.isPixHabilitado());
        dados.put("parcelamentoHabilitado", configuracao.isParcelamentoHabilitado());
        dados.put("parcelasMinimas", configuracao.getParcelasMinimas());
        dados.put("parcelasMaximas", configuracao.getParcelasMaximas());
        dados.put("valorMinimoParcela", configuracao.getValorMinimoParcela());
        dados.put("politicaJuros", configuracao.getPoliticaJuros().name());
        dados.put("validadeCobrancaSegundos", configuracao.getValidadeCobrancaSegundos());
        dados.put("limiteDescontoPercentual", configuracao.getLimiteDescontoPercentual());
        dados.put("limiteDescontoValor", configuracao.getLimiteDescontoValor());
        dados.put("imprimirComprovante", configuracao.isImprimirComprovante());
        dados.put("permitirPagamentoManual", configuracao.isPermitirPagamentoManual());
        return dados;
    }

    public static boolean mesmoEstado(ConfiguracaoCaixaEntity configuracao, ConfiguracaoCaixaRequestDto dto) {
        return configuracao.isPointHabilitado() == dto.pointHabilitado()
                && configuracao.isDinheiroHabilitado() == dto.dinheiroHabilitado()
                && configuracao.isDebitoHabilitado() == dto.debitoHabilitado()
                && configuracao.isCreditoHabilitado() == dto.creditoHabilitado()
                && configuracao.isPixHabilitado() == dto.pixHabilitado()
                && configuracao.isParcelamentoHabilitado() == dto.parcelamentoHabilitado()
                && configuracao.getParcelasMinimas() == dto.parcelasMinimas()
                && configuracao.getParcelasMaximas() == dto.parcelasMaximas()
                && iguais(configuracao.getValorMinimoParcela(), escala(dto.valorMinimoParcela()))
                && configuracao.getPoliticaJuros() == dto.politicaJuros()
                && configuracao.getValidadeCobrancaSegundos() == dto.validadeCobrancaSegundos()
                && iguais(configuracao.getLimiteDescontoPercentual(), escala(dto.limiteDescontoPercentual()))
                && iguais(configuracao.getLimiteDescontoValor(), escala(dto.limiteDescontoValor()))
                && configuracao.isImprimirComprovante() == dto.imprimirComprovante()
                && configuracao.isPermitirPagamentoManual() == dto.permitirPagamentoManual();
    }

    private static boolean iguais(Object atual, Object novo) {
        if (atual instanceof BigDecimal valorAtual && novo instanceof BigDecimal valorNovo) {
            return valorAtual.compareTo(valorNovo) == 0;
        }
        return atual == null ? novo == null : atual.equals(novo);
    }

    private static BigDecimal escala(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
