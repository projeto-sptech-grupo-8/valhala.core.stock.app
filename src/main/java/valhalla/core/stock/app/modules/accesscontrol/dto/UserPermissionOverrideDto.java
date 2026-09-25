package valhalla.core.stock.app.modules.accesscontrol.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import valhalla.core.stock.app.modules.accesscontrol.entity.PermissionEffect;

public record UserPermissionOverrideDto(@NotBlank String functionalityCode,
                                        @NotNull PermissionEffect effect) { }
