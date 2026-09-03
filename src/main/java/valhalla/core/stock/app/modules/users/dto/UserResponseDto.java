package valhalla.core.stock.app.modules.users.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponseDto(
        UUID id,
        String name,
        String email,
        String phone,
        UUID profileId,
        String profileName,
        Boolean active,
        LocalDateTime updatedAt,
        LocalDateTime createdAt
) {}
