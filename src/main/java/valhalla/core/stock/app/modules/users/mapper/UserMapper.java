package valhalla.core.stock.app.modules.users.mapper;

import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.users.dto.UserCreateRequestDto;
import valhalla.core.stock.app.modules.users.dto.UserResponseDto;
import valhalla.core.stock.app.modules.users.entity.UserEntity;

import java.util.Locale;
import valhalla.core.stock.app.modules.users.entity.UserStatus;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserEntity toEntity(
            UserCreateRequestDto dto,
            String passwordHash,
            ProfileEntity profile
    ) {
        return UserEntity.builder()
                .name(dto.name().trim())
                .email(dto.email().trim().toLowerCase(Locale.ROOT))
                .phone(normalizePhone(dto.phone()))
                .passwordHash(passwordHash)
                .profile(profile)
                .establishment(profile.getEstablishment())
                .status(UserStatus.ATIVO)
                .build();
    }

    public static UserResponseDto toResponse(UserEntity user) {
        return new UserResponseDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getEstablishment().getId(),
                user.getProfile().getId(),
                user.getProfile().getName(),
                user.isActive(),
                user.getUpdatedAt(),
                user.getCreatedAt()
        );
    }

    private static String normalizePhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.trim();
    }
}
