package valhalla.core.stock.app.modules.accesscontrol.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import valhalla.core.stock.app.modules.users.security.UserAuthorizationService;

@Component("permissionAuthorizationService")
public class PermissionAuthorizationService {

    public boolean hasPermission(String functionalityCode, Authentication authentication) {
        return UserAuthorizationService.isManagerRole(authentication) || authentication != null
                && authentication.getAuthorities().stream().anyMatch(authority ->
                functionalityCode.equals(authority.getAuthority()));
    }

    public boolean canManageUsers(Authentication authentication) {
        return hasPermission("GERENCIAR_USUARIOS", authentication);
    }
}
