package valhalla.core.stock.app.modules.caixa.pedido.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sequencia_documento")
@IdClass(SequenciaDocumentoId.class)
@Getter
@Setter
@NoArgsConstructor
public class SequenciaDocumentoEntity {

    @Id
    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @Id
    @Column(name = "tipo", nullable = false, length = 30)
    private String tipo;

    @Column(name = "ultimo_numero", nullable = false)
    private Long ultimoNumero = 0L;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    @PreUpdate
    private void atualizarTimestamp() {
        atualizadoEm = LocalDateTime.now();
        if (ultimoNumero == null) {
            ultimoNumero = 0L;
        }
    }
}
