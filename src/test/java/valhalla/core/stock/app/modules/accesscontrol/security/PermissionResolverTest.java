package valhalla.core.stock.app.modules.accesscontrol.security;

import org.junit.jupiter.api.Test;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.PermissionEffect;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.UserFunctionalityOverrideEntity;
import valhalla.core.stock.app.modules.users.entity.UserEntity;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PermissionResolverTest {

    @Test
    void deveAplicarGrantERevokeSobreAsPermissoesDoPerfil() {
        FuncionalidadeEntity visualizar = funcionalidade("USUARIOS_VISUALIZAR");
        FuncionalidadeEntity criar = funcionalidade("USUARIOS_CRIAR");
        FuncionalidadeEntity editar = funcionalidade("USUARIOS_EDITAR");
        ProfileEntity perfil = ProfileEntity.builder().functionalities(Set.of(visualizar, editar)).build();
        UserEntity usuario = UserEntity.builder().profile(perfil).functionalityOverrides(Set.of(
                sobrescrita(criar, PermissionEffect.GRANT),
                sobrescrita(editar, PermissionEffect.REVOKE))).build();

        assertEquals(Set.of("USUARIOS_VISUALIZAR", "USUARIOS_CRIAR"),
                PermissionResolver.effectiveCodes(usuario));
    }

    private FuncionalidadeEntity funcionalidade(String codigo) {
        return FuncionalidadeEntity.builder().code(codigo).build();
    }

    private UserFunctionalityOverrideEntity sobrescrita(FuncionalidadeEntity funcionalidade,
                                                        PermissionEffect efeito) {
        return UserFunctionalityOverrideEntity.builder().functionality(funcionalidade)
                .effect(efeito).build();
    }
}
