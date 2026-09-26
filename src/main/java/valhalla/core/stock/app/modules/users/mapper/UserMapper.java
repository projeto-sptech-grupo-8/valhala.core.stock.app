package valhalla.core.stock.app.modules.users.mapper;

import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.users.dto.RequisicaoCriacaoUsuarioDto;
import valhalla.core.stock.app.modules.users.dto.RespostaUsuarioDto;
import valhalla.core.stock.app.modules.users.entity.UserEntity;

import java.util.Locale;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.accesscontrol.security.PermissionResolver;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserEntity toEntity(
            RequisicaoCriacaoUsuarioDto dto,
            String passwordHash,
            ProfileEntity profile
    ) {
        return UserEntity.builder()
                .name(dto.nome().trim())
                .email(dto.email().trim().toLowerCase(Locale.ROOT))
                .phone(normalizePhone(dto.telefone()))
                .passwordHash(passwordHash)
                .profile(profile)
                .establishment(profile.getEstablishment())
                .status(UserStatus.ATIVO)
                .build();
    }

    public static RespostaUsuarioDto toResponse(UserEntity user) {
        return new RespostaUsuarioDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getEstablishment().getId(),
                user.getProfile().getId(),
                user.getProfile().getName(),
                user.isActive(),
                user.getUpdatedAt(),
                user.getCreatedAt(),
                PermissionResolver.effectiveCodes(user)
        );
    }

    private static String normalizePhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.trim();
    }
}
