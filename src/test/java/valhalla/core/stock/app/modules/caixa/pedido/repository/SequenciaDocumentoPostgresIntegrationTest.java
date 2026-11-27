package valhalla.core.stock.app.modules.caixa.pedido.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.support.PostgresIntegrationTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

class SequenciaDocumentoPostgresIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private SequenciaDocumentoRepository sequenciaDocumentoRepository;

    @Test
    void proximoNumeroIncrementaAtomicamenteComOnConflictReturningNoPostgresEIsolaEstabelecimentos() {
        EstablishmentEntity outroEstabelecimento = entityManager.merge(EstablishmentEntity.builder()
                .corporateName("Outra Loja LTDA")
                .tradeName("Outra Loja")
                .cnpj("98765432000188")
                .active(true)
                .build());

        assertThat(sequenciaDocumentoRepository.proximoNumero(estabelecimento.getId(), "PEDIDO")).isEqualTo(1L);
        assertThat(sequenciaDocumentoRepository.proximoNumero(estabelecimento.getId(), "PEDIDO")).isEqualTo(2L);
        assertThat(sequenciaDocumentoRepository.proximoNumero(estabelecimento.getId(), "PEDIDO")).isEqualTo(3L);

        assertThat(sequenciaDocumentoRepository.proximoNumero(estabelecimento.getId(), "NFCE")).isEqualTo(1L);
        assertThat(sequenciaDocumentoRepository.proximoNumero(estabelecimento.getId(), "NFCE")).isEqualTo(2L);

        assertThat(sequenciaDocumentoRepository.proximoNumero(outroEstabelecimento.getId(), "PEDIDO"))
                .isEqualTo(1L);
    }
}
