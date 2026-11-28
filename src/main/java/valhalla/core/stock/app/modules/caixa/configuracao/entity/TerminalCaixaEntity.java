package valhalla.core.stock.app.modules.caixa.configuracao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import valhalla.core.stock.app.shared.time.FusoHorarioOperacao;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "terminal_caixa")
@Getter
@Setter
@NoArgsConstructor
public class TerminalCaixaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "caixa_id", nullable = false)
    private CaixaEntity caixa;

    @Column(name = "terminal_id", nullable = false, length = 100)
    private String terminalId;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    private void prePersist() {
        LocalDateTime agora = FusoHorarioOperacao.agora();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        if (atualizadoEm == null) {
            atualizadoEm = agora;
        }
    }

    @PreUpdate
    private void preUpdate() {
        atualizadoEm = FusoHorarioOperacao.agora();
    }
}
