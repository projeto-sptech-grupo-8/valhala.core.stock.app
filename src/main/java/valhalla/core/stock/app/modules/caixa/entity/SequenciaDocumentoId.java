package valhalla.core.stock.app.modules.caixa.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SequenciaDocumentoId implements Serializable {
    private UUID estabelecimentoId;
    private String tipo;
}
