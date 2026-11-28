package valhalla.core.stock.app.modules.caixa.pedido.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.caixa.pedido.entity.SequenciaDocumentoEntity;
import valhalla.core.stock.app.modules.caixa.pedido.entity.SequenciaDocumentoId;
import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

public class SequenciaDocumentoRepositoryCustomImpl implements SequenciaDocumentoRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    private volatile Boolean postgres;

    @Override
    @Transactional
    public Long proximoNumero(UUID estabelecimentoId, String tipo) {
        if (ehPostgres()) {
            Object resultado = entityManager.createNativeQuery("""
                            INSERT INTO sequencia_documento (estabelecimento_id, tipo, ultimo_numero, atualizado_em)
                            VALUES (:estabelecimentoId, :tipo, 1, :agora)
                            ON CONFLICT (estabelecimento_id, tipo)
                            DO UPDATE SET
                                ultimo_numero = sequencia_documento.ultimo_numero + 1,
                                atualizado_em = EXCLUDED.atualizado_em
                            RETURNING ultimo_numero
                            """)
                    .setParameter("estabelecimentoId", estabelecimentoId)
                    .setParameter("tipo", tipo)
                    .setParameter("agora", FusoHorarioOperacao.agora())
                    .getSingleResult();
            return ((Number) resultado).longValue();
        }

        SequenciaDocumentoId id = new SequenciaDocumentoId(estabelecimentoId, tipo);
        SequenciaDocumentoEntity sequencia = entityManager.find(
                SequenciaDocumentoEntity.class,
                id,
                LockModeType.PESSIMISTIC_WRITE
        );
        LocalDateTime agora = FusoHorarioOperacao.agora();
        if (sequencia == null) {
            SequenciaDocumentoEntity nova = new SequenciaDocumentoEntity();
            nova.setEstabelecimentoId(estabelecimentoId);
            nova.setTipo(tipo);
            nova.setUltimoNumero(1L);
            nova.setAtualizadoEm(agora);
            entityManager.persist(nova);
            entityManager.flush();
            return 1L;
        }

        long proximo = (sequencia.getUltimoNumero() == null ? 0L : sequencia.getUltimoNumero()) + 1L;
        sequencia.setUltimoNumero(proximo);
        sequencia.setAtualizadoEm(agora);
        entityManager.flush();
        return proximo;
    }

    private boolean ehPostgres() {
        Boolean cached = postgres;
        if (cached != null) {
            return cached;
        }
        String produto = entityManager.unwrap(Session.class)
                .doReturningWork(connection -> connection.getMetaData().getDatabaseProductName());
        boolean detectado = produto != null && produto.toLowerCase(Locale.ROOT).contains("postgres");
        postgres = detectado;
        return detectado;
    }
}
