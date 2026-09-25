package valhalla.core.stock.app.modules.accesscontrol.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record UserPermissionOverridesRequestDto(
        @NotNull Set<@Valid UserPermissionOverrideDto> overrides) { }
