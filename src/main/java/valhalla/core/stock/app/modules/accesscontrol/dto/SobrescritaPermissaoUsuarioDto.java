package valhalla.core.stock.app.modules.accesscontrol.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import valhalla.core.stock.app.modules.accesscontrol.entity.PermissionEffect;

public record SobrescritaPermissaoUsuarioDto(@NotBlank @JsonAlias("functionalityCode") String codigoFuncionalidade,
                                             @NotNull @JsonAlias("effect") PermissionEffect efeito) { }
