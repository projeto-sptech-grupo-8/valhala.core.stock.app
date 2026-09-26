package valhalla.core.stock.app.modules.accesscontrol.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record RequisicaoSobrescritasPermissaoUsuarioDto(
        @NotNull @JsonAlias("overrides") Set<@Valid SobrescritaPermissaoUsuarioDto> sobrescritas) { }
