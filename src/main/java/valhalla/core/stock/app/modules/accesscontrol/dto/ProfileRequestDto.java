package valhalla.core.stock.app.modules.accesscontrol.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record ProfileRequestDto(
        @NotBlank @Size(max = 100) String name,
        String description,
        Set<String> functionalityCodes
) { }
