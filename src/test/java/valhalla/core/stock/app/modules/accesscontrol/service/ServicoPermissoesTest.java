package valhalla.core.stock.app.modules.accesscontrol.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import valhalla.core.stock.app.modules.accesscontrol.dto.RequisicaoAtualizacaoPerfilDto;
import valhalla.core.stock.app.modules.accesscontrol.dto.RequisicaoPerfilDto;
import valhalla.core.stock.app.modules.accesscontrol.entity.FuncionalidadeEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.PermissionEffect;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.entity.UserFunctionalityOverrideEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.FuncionalidadeRepository;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.auth.service.TokenStateService;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.establishments.repository.EstablishmentRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.shared.error.AccessConfigurationConflictException;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicoPermissoesTest {

    @Mock private ProfileRepository repositorioPerfil;
    @Mock private FuncionalidadeRepository repositorioFuncionalidade;
    @Mock private UserRepository repositorioUsuario;
    @Mock private TokenStateService servicoEstadoToken;
    @Mock private EstablishmentRepository repositorioEstabelecimento;

    private ServicoPermissoes servicoPermissoes;
    private UUID idEstabelecimento;
    private JwtAuthenticationToken autenticacao;

    @BeforeEach
    void preparar() {
        servicoPermissoes = new ServicoPermissoes(repositorioPerfil, repositorioFuncionalidade,
                repositorioUsuario, servicoEstadoToken, repositorioEstabelecimento,
                new BusinessEventLogger());
        idEstabelecimento = UUID.randomUUID();
        Jwt token = Jwt.withTokenValue("teste").header("alg", "none")
                .claim("establishmentId", idEstabelecimento.toString()).build();
        autenticacao = new JwtAuthenticationToken(token);
    }

    @Test
    void naoDeveRemoverAPermissaoDoUltimoUsuarioQuePodeCriarUsuarios() {
        UserEntity usuario = usuario("Atendente", Set.of(sobrescrita("USUARIOS_CRIAR")));
        when(repositorioUsuario.findById(usuario.getId())).thenReturn(Optional.of(usuario));
        when(repositorioFuncionalidade.findByCodeIn(Set.of())).thenReturn(List.of());
        when(repositorioUsuario.findAllByEstablishment_Id(idEstabelecimento)).thenReturn(List.of(usuario));

        assertThrows(AccessConfigurationConflictException.class,
                () -> servicoPermissoes.substituirSobrescritasPermissaoUsuario(
                        usuario.getId(), Set.of(), autenticacao));

        verify(repositorioUsuario, never()).save(any());
        verify(servicoEstadoToken, never()).revokeAll(any());
    }

    @Test
    void gerenteAtivoPermaneceAdministradorMesmoSemFuncionalidadesNoPerfil() {
        UserEntity gerente = usuario("Gerente", Set.of());
        when(repositorioUsuario.findById(gerente.getId())).thenReturn(Optional.of(gerente));
        when(repositorioFuncionalidade.findByCodeIn(Set.of())).thenReturn(List.of());
        when(repositorioUsuario.findAllByEstablishment_Id(idEstabelecimento)).thenReturn(List.of(gerente));

        assertDoesNotThrow(() -> servicoPermissoes.substituirSobrescritasPermissaoUsuario(
                gerente.getId(), Set.of(), autenticacao));

        verify(repositorioUsuario).save(gerente);
        verify(servicoEstadoToken).revokeAll(gerente.getId());
    }

    @Test
    void naoDeveCriarPerfilComNomeJaExistenteSemDiferenciarMaiusculas() {
        ProfileEntity perfilExistente = perfil("Atendente");
        when(repositorioPerfil.findByEstablishment_IdAndNameIgnoreCase(idEstabelecimento, "atendente"))
                .thenReturn(Optional.of(perfilExistente));

        assertThrows(AccessConfigurationConflictException.class,
                () -> servicoPermissoes.criarPerfil(new RequisicaoPerfilDto("atendente", null, Set.of()), autenticacao));

        verify(repositorioPerfil, never()).saveAndFlush(any());
    }

    @Test
    void naoDeveAtualizarPerfilComNomeEmBranco() {
        ProfileEntity perfil = perfil("Atendente");
        when(repositorioPerfil.findById(perfil.getId())).thenReturn(Optional.of(perfil));

        assertThrows(IllegalArgumentException.class,
                () -> servicoPermissoes.atualizarPerfil(perfil.getId(),
                        new RequisicaoAtualizacaoPerfilDto("   ", null), autenticacao));

        verify(repositorioPerfil, never()).saveAndFlush(any());
    }

    private UserEntity usuario(String nomePerfil, Set<UserFunctionalityOverrideEntity> sobrescritas) {
        ProfileEntity perfil = perfil(nomePerfil);
        UserEntity usuario = UserEntity.builder().id(UUID.randomUUID()).profile(perfil)
                .establishment(perfil.getEstablishment()).status(UserStatus.ATIVO)
                .functionalityOverrides(sobrescritas).build();
        sobrescritas.forEach(sobrescrita -> sobrescrita.setUser(usuario));
        return usuario;
    }

    private ProfileEntity perfil(String nome) {
        return ProfileEntity.builder().id(1).name(nome)
                .establishment(EstablishmentEntity.builder().id(idEstabelecimento).build())
                .functionalities(Set.of()).build();
    }

    private UserFunctionalityOverrideEntity sobrescrita(String codigo) {
        return UserFunctionalityOverrideEntity.builder()
                .functionality(FuncionalidadeEntity.builder().code(codigo).build())
                .effect(PermissionEffect.GRANT).build();
    }
}
