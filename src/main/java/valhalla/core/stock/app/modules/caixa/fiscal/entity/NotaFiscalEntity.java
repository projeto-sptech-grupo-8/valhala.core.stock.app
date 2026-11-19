package valhalla.core.stock.app.modules.caixa.fiscal.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.AmbienteFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.StatusNotaFiscal;
import valhalla.core.stock.app.modules.caixa.fiscal.enums.TipoNotaFiscal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nota_fiscal")
@Getter
@Setter
@NoArgsConstructor
public class NotaFiscalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "estabelecimento_id", nullable = false)
    private UUID estabelecimentoId;

    @Column(name = "pedido_id")
    private UUID pedidoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoNotaFiscal tipo = TipoNotaFiscal.NFCE;

    @Column(name = "modelo", nullable = false, length = 2)
    private String modelo = "65";

    @Enumerated(EnumType.STRING)
    @Column(name = "ambiente", nullable = false, length = 15)
    private AmbienteFiscal ambiente = AmbienteFiscal.HOMOLOGACAO;

    @Column(name = "provedor", nullable = false, length = 30)
    private String provedor = "INTERNO";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusNotaFiscal status = StatusNotaFiscal.AUTORIZADA;

    @Column(name = "numero", length = 20)
    private String numero;

    @Column(name = "serie", length = 10)
    private String serie = "1";

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "chave_acesso", length = 44, columnDefinition = "CHAR(44)", unique = true)
    private String chaveAcesso;

    @Column(name = "protocolo_autorizacao", length = 50)
    private String protocoloAutorizacao;

    @Column(name = "url_consulta", length = 500)
    private String urlConsulta;

    @Column(name = "contraparte_nome", length = 150)
    private String contraparteNome;

    @Column(name = "contraparte_documento", length = 20)
    private String contraparteDocumento;

    @Column(name = "valor_produtos", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorProdutos = BigDecimal.ZERO;

    @Column(name = "valor_desconto", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorDesconto = BigDecimal.ZERO;

    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dados_fiscais")
    private JsonNode dadosFiscais;

    @Column(name = "xml_conteudo", columnDefinition = "TEXT")
    private String xmlConteudo;

    @Column(name = "emitida_por_usuario_id")
    private UUID emitidaPorUsuarioId;

    @Column(name = "emitida_em")
    private LocalDateTime emitidaEm;

    @Column(name = "cancelada_em")
    private LocalDateTime canceladaEm;

    @Column(name = "cancelada_por_usuario_id")
    private UUID canceladaPorUsuarioId;

    @Column(name = "motivo_cancelamento", columnDefinition = "TEXT")
    private String motivoCancelamento;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "atualizada_em", nullable = false)
    private LocalDateTime atualizadaEm;

    @PrePersist
    private void prePersist() {
        LocalDateTime agora = LocalDateTime.now();
        if (criadaEm == null) {
            criadaEm = agora;
        }
        if (atualizadaEm == null) {
            atualizadaEm = agora;
        }
        if (tipo == null) {
            tipo = TipoNotaFiscal.NFCE;
        }
        if (modelo == null || modelo.isBlank()) {
            modelo = "65";
        }
        if (ambiente == null) {
            ambiente = AmbienteFiscal.HOMOLOGACAO;
        }
        if (provedor == null || provedor.isBlank()) {
            provedor = "INTERNO";
        }
        if (status == null) {
            status = StatusNotaFiscal.AUTORIZADA;
        }
        if (serie == null || serie.isBlank()) {
            serie = "1";
        }
        if (valorProdutos == null) {
            valorProdutos = BigDecimal.ZERO;
        }
        if (valorDesconto == null) {
            valorDesconto = BigDecimal.ZERO;
        }
        if (valorTotal == null) {
            valorTotal = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    private void preUpdate() {
        atualizadaEm = LocalDateTime.now();
    }
}
