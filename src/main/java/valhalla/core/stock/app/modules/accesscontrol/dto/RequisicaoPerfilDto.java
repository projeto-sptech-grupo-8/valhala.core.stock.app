package valhalla.core.stock.app.modules.accesscontrol.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record RequisicaoPerfilDto(
        @NotBlank @Size(max = 100) @JsonAlias("name") String nome,
        @JsonAlias("description") String descricao,
        @JsonAlias("functionalityCodes") Set<String> codigosFuncionalidades
) { }
