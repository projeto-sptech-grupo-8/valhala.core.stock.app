package valhalla.core.stock.app.modules.accesscontrol.dto;

import java.util.Set;

/** Dados que a tela administrativa usa para explicar e editar os acessos. */
public record UserPermissionsResponseDto(
        Integer profileId,
        String profileName,
        Set<String> profilePermissions,
        Set<UserPermissionOverrideDto> overrides,
        Set<String> effectivePermissions
) { }
