package valhalla.core.stock.app.modules.users.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.support.AutenticacaoTeste;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthorizationServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserAuthorizationService authorizationService;
    private UUID establishmentId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        authorizationService = new UserAuthorizationService(userRepository);
        establishmentId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        AutenticacaoTeste.limpar();
    }

    @Test
    void canViewDeveRetornarFalsoParaAutenticacaoNulaOuNaoAutenticada() {
        assertThat(authorizationService.canView(userId, null)).isFalse();

        Authentication unauth = new UsernamePasswordAuthenticationToken("user", "pass");
        assertThat(authorizationService.canView(userId, unauth)).isFalse();
    }

    @Test
    void canViewDevePermitirUsuarioConsultarApenasSeuProprioIdSemPermissaoGeral() {
        JwtAuthenticationToken auth = AutenticacaoTeste.token(establishmentId, userId, "ROLE_ATENDENTE");

        assertThat(authorizationService.canView(userId, auth)).isTrue();
        assertThat(authorizationService.canView(UUID.randomUUID(), auth)).isFalse();
    }

    @Test
    void canViewComPermissaoDeveValidarEstabelecimentoDoUsuario() {
        JwtAuthenticationToken auth = AutenticacaoTeste.token(establishmentId, userId, "USUARIOS_VISUALIZAR");
        UUID targetId = UUID.randomUUID();

        // 1. Usuário não existe ainda no banco: permite para o service devolver 404
        when(userRepository.existsById(targetId)).thenReturn(false);
        assertThat(authorizationService.canView(targetId, auth)).isTrue();

        // 2. Usuário existe no mesmo estabelecimento: permite
        when(userRepository.existsById(targetId)).thenReturn(true);
        when(userRepository.existsByIdAndEstablishment_Id(targetId, establishmentId)).thenReturn(true);
        assertThat(authorizationService.canView(targetId, auth)).isTrue();

        // 3. Usuário existe em outro estabelecimento: bloqueia
        when(userRepository.existsByIdAndEstablishment_Id(targetId, establishmentId)).thenReturn(false);
        assertThat(authorizationService.canView(targetId, auth)).isFalse();
    }

    @Test
    void canViewDeveRetornarFalsoSeTokenNaoTemEstabelecimento() {
        JwtAuthenticationToken authSemEstabelecimento = AutenticacaoTeste.token(null, userId, "USUARIOS_VISUALIZAR");
        assertThat(authorizationService.canView(UUID.randomUUID(), authSemEstabelecimento)).isFalse();
    }

    @Test
    void canUpdateDevePermitirProprioUsuarioOuOperadorComPermissaoNoMesmoEstabelecimento() {
        JwtAuthenticationToken authSelf = AutenticacaoTeste.token(establishmentId, userId, "ROLE_ATENDENTE");
        assertThat(authorizationService.canUpdate(userId, authSelf)).isTrue();

        UUID outroId = UUID.randomUUID();
        assertThat(authorizationService.canUpdate(outroId, authSelf)).isFalse();

        JwtAuthenticationToken authAdmin = AutenticacaoTeste.token(establishmentId, userId, "USUARIOS_EDITAR");
        when(userRepository.existsById(outroId)).thenReturn(true);
        when(userRepository.existsByIdAndEstablishment_Id(outroId, establishmentId)).thenReturn(true);
        assertThat(authorizationService.canUpdate(outroId, authAdmin)).isTrue();

        when(userRepository.existsByIdAndEstablishment_Id(outroId, establishmentId)).thenReturn(false);
        assertThat(authorizationService.canUpdate(outroId, authAdmin)).isFalse();
    }

    @Test
    void canDeleteDevePermitirProprioUsuarioOuComPermissaoNoMesmoEstabelecimento() {
        JwtAuthenticationToken authSelf = AutenticacaoTeste.token(establishmentId, userId, "ROLE_ATENDENTE");
        assertThat(authorizationService.canDelete(userId, authSelf)).isTrue();

        UUID outroId = UUID.randomUUID();
        JwtAuthenticationToken authExcluir = AutenticacaoTeste.token(establishmentId, userId, "USUARIOS_EXCLUIR");

        when(userRepository.existsById(outroId)).thenReturn(false);
        assertThat(authorizationService.canDelete(outroId, authExcluir)).isTrue();

        when(userRepository.existsById(outroId)).thenReturn(true);
        when(userRepository.existsByIdAndEstablishment_Id(outroId, establishmentId)).thenReturn(true);
        assertThat(authorizationService.canDelete(outroId, authExcluir)).isTrue();
    }

    @Test
    void isManagerDeveVerificarRoleERegistroNoRepositorio() {
        JwtAuthenticationToken authSemRole = AutenticacaoTeste.token(establishmentId, userId, "ROLE_ATENDENTE");
        assertThat(authorizationService.isManager(authSemRole)).isFalse();

        JwtAuthenticationToken authGerente = AutenticacaoTeste.token(establishmentId, userId, "ROLE_GERENTE");
        when(userRepository.existsByIdAndProfile_NameIgnoreCase(userId, "Gerente")).thenReturn(true);
        assertThat(authorizationService.isManager(authGerente)).isTrue();

        when(userRepository.existsByIdAndProfile_NameIgnoreCase(userId, "Gerente")).thenReturn(false);
        assertThat(authorizationService.isManager(authGerente)).isFalse();
    }

    @Test
    void isManagerSemUserIdNoTokenDeveConsultarPorEmail() {
        Authentication authEmail = new UsernamePasswordAuthenticationToken(
                "gerente@meraki.com", "pass", List.of(new SimpleGrantedAuthority("ROLE_GERENTE")));

        when(userRepository.existsByEmailIgnoreCaseAndProfile_NameIgnoreCase("gerente@meraki.com", "Gerente"))
                .thenReturn(true);

        assertThat(authorizationService.isManager(authEmail)).isTrue();
    }
}
