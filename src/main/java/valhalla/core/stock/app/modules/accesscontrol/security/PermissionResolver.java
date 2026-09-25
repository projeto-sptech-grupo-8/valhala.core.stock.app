package valhalla.core.stock.app.modules.accesscontrol.security;

import valhalla.core.stock.app.modules.accesscontrol.entity.PermissionEffect;
import valhalla.core.stock.app.modules.users.entity.UserEntity;

import java.util.LinkedHashSet;
import java.util.Set;

/** Centraliza a regra de permissões efetivas para autenticação e respostas da API. */
public final class PermissionResolver {
    private PermissionResolver() { }

    public static Set<String> effectiveCodes(UserEntity user) {
        Set<String> permissions = new LinkedHashSet<>();
        user.getProfile().getFunctionalities().forEach(item ->
                permissions.add(item.getCode()));
        user.getFunctionalityOverrides().forEach(override -> {
            String code = override.getFunctionality().getCode();
            if (override.getEffect() == PermissionEffect.GRANT) permissions.add(code);
            else permissions.remove(code);
        });
        return Set.copyOf(permissions);
    }
}
