package valhalla.core.stock.app.modules.estoque.entity;

import java.text.Normalizer;
import java.util.Locale;

/** Classificação obrigatória das movimentações do tipo PERDA, usada no relatório de perdas. */
public enum MotivoPerda {
    QUEBRA("Perda / quebra"),
    VENCIMENTO("Vencimento"),
    CONSUMO_INTERNO("Consumo interno");

    private final String rotulo;

    MotivoPerda(String rotulo) {
        this.rotulo = rotulo;
    }

    public String rotulo() {
        return rotulo;
    }

    /**
     * Compatibilidade com clientes que ainda não enviam {@code motivoPerda}: deduz a classificação
     * pelo texto livre do motivo. Sem correspondência, a perda é tratada como quebra.
     */
    public static MotivoPerda inferir(String motivo) {
        if (motivo == null) {
            return QUEBRA;
        }
        String texto = Normalizer.normalize(motivo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        if (texto.contains("vencid") || texto.contains("vencimento")) {
            return VENCIMENTO;
        }
        if (texto.contains("consumo interno")) {
            return CONSUMO_INTERNO;
        }
        return QUEBRA;
    }
}
