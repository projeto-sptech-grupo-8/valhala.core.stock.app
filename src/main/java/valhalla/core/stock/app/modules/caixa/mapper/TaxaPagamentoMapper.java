package valhalla.core.stock.app.modules.caixa.mapper;

import valhalla.core.stock.app.modules.caixa.dto.TaxaPagamentoResponseDto;
import valhalla.core.stock.app.modules.caixa.entity.PoliticaJurosCaixa;
import valhalla.core.stock.app.modules.caixa.entity.TaxaPagamentoEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TaxaPagamentoMapper {

    private TaxaPagamentoMapper() {
    }

    public static TaxaPagamentoResponseDto paraResposta(
            TaxaPagamentoEntity taxa,
            PoliticaJurosCaixa politicaGlobal
    ) {
        return new TaxaPagamentoResponseDto(
                taxa.getId(),
                taxa.getForma(),
                taxa.getParcelas(),
                taxa.getTaxaPercentual(),
                taxa.getTaxaFixa(),
                taxa.getRepassarAoCliente(),
                repasseEfetivo(taxa, politicaGlobal),
                taxa.isAtivo(),
                taxa.getCriadoEm(),
                taxa.getAtualizadoEm()
        );
    }

    public static boolean repasseEfetivo(TaxaPagamentoEntity taxa, PoliticaJurosCaixa politicaGlobal) {
        if (taxa.getRepassarAoCliente() != null) {
            return taxa.getRepassarAoCliente();
        }
        return politicaGlobal == PoliticaJurosCaixa.REPASSADA_AO_CLIENTE;
    }

    public static Map<String, Object> snapshot(TaxaPagamentoEntity taxa) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("forma", taxa.getForma().name());
        dados.put("parcelas", taxa.getParcelas());
        dados.put("taxaPercentual", taxa.getTaxaPercentual());
        dados.put("taxaFixa", taxa.getTaxaFixa());
        dados.put("repassarAoCliente", taxa.getRepassarAoCliente());
        dados.put("ativo", taxa.isAtivo());
        return dados;
    }

    public static BigDecimal escalaPercentual(BigDecimal valor) {
        return valor.setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal escalaValor(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
