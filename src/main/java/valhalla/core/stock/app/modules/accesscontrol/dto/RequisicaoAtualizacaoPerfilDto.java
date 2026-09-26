package valhalla.core.stock.app.modules.accesscontrol.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RequisicaoAtualizacaoPerfilDto(
        @Size(max = 100)
        @Pattern(regexp = ".*\\S.*", message = "O nome do perfil não pode ficar vazio")
        @JsonAlias("name") String nome,
        @JsonAlias("description") String descricao
) { }
