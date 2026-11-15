package valhalla.core.stock.app.modules.estoque.entity;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Situação de um lote frente à validade, na data de referência (hoje). Vence hoje ainda é vendável. */
public enum SituacaoValidadeLote {
    VENCIDO,
    VENCE_EM_7_DIAS,
    VENCE_EM_30_DIAS,
    VALIDO,
    SEM_VALIDADE;

    public static SituacaoValidadeLote de(LocalDate validade, LocalDate hoje) {
        if (validade == null) {
            return SEM_VALIDADE;
        }
        long dias = ChronoUnit.DAYS.between(hoje, validade);
        if (dias < 0) {
            return VENCIDO;
        }
        if (dias <= 7) {
            return VENCE_EM_7_DIAS;
        }
        return dias <= 30 ? VENCE_EM_30_DIAS : VALIDO;
    }
}
