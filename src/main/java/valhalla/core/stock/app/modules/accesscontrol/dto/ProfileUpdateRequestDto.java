package valhalla.core.stock.app.modules.accesscontrol.dto;

import jakarta.validation.constraints.Size;

public record ProfileUpdateRequestDto(
        @Size(min = 1, max = 100) String name,
        String description
) { }
