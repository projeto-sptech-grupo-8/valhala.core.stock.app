package valhalla.core.stock.app.shared.time;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Fuso horário da operação. Os horários de negócio (movimentações, vendas) são gravados em colunas
 * TIMESTAMP sem fuso e os relatórios recortam períodos e horas nesse mesmo fuso. Por isso a gravação
 * não pode depender do fuso padrão da JVM, que costuma ser UTC em servidores.
 */
public final class FusoHorarioOperacao {

    public static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private FusoHorarioOperacao() {
    }

    public static LocalDateTime agora() {
        return LocalDateTime.now(ZONA);
    }
}
