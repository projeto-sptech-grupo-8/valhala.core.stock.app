package valhalla.core.stock.app.shared.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class FusoHorarioOperacaoTest {

    @Test
    void deveUsarZonaAmericaSaoPauloParaAgoraHojeERelogio() {
        assertThat(FusoHorarioOperacao.ZONA).isEqualTo(ZoneId.of("America/Sao_Paulo"));

        Clock relogio = FusoHorarioOperacao.relogio();
        assertThat(relogio.getZone()).isEqualTo(FusoHorarioOperacao.ZONA);

        LocalDateTime antes = LocalDateTime.now(FusoHorarioOperacao.ZONA).minusSeconds(1);
        LocalDateTime agora = FusoHorarioOperacao.agora();
        LocalDateTime depois = LocalDateTime.now(FusoHorarioOperacao.ZONA).plusSeconds(1);
        assertThat(agora).isBetween(antes, depois);

        LocalDate hojeEsperado = LocalDate.now(FusoHorarioOperacao.ZONA);
        assertThat(FusoHorarioOperacao.hoje()).isEqualTo(hojeEsperado);
    }
}
