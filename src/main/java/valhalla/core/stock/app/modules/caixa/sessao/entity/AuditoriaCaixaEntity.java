package valhalla.core.stock.app.modules.caixa.sessao.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import valhalla.core.stock.app.modules.caixa.sessao.enums.OrigemAuditoriaCaixa;

@Entity
@Table(name = "auditoria_caixa")
@Getter
@Setter
@NoArgsConstructor
public class AuditoriaCaixaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @Column(name = "sessao_caixa_id")
    private UUID sessaoCaixaId;

    @Column(name = "usuario_id")
    private UUID usuarioId;

    @Column(name = "usuario_nome", length = 150)
    private String usuarioNome;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 20)
    private OrigemAuditoriaCaixa origem = OrigemAuditoriaCaixa.API;

    @Column(name = "tipo_evento", nullable = false, length = 60)
    private String tipoEvento;

    @Column(name = "entidade", length = 40)
    private String entidade;

    @Column(name = "entidade_id", length = 64)
    private String entidadeId;

    @Column(name = "pedido_id")
    private UUID pedidoId;

    @Column(name = "pagamento_pedido_id")
    private UUID pagamentoPedidoId;

    @Column(name = "descricao", columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "valor", precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(name = "motivo", columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "autorizado_por_usuario_id")
    private UUID autorizadoPorUsuarioId;

    @Column(name = "autorizado_por_nome", length = 150)
    private String autorizadoPorNome;

    @Column(name = "pedido_identificador", length = 60)
    private String pedidoIdentificador;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_anteriores")
    private JsonNode dadosAnteriores;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_novos")
    private JsonNode dadosNovos;

    @Column(name = "ocorrido_em", nullable = false)
    private LocalDateTime ocorridoEm;

    @PrePersist
    private void prePersist() {
        if (ocorridoEm == null) {
            ocorridoEm = LocalDateTime.now();
        }
        if (origem == null) {
            origem = OrigemAuditoriaCaixa.API;
        }
    }
}
