package valhalla.core.stock.app.modules.users.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.accesscontrol.service.ServicoPermissoes;
import valhalla.core.stock.app.modules.auth.service.TokenStateService;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import valhalla.core.stock.app.modules.users.dto.RequisicaoAtualizacaoUsuarioDto;
import valhalla.core.stock.app.modules.users.dto.RequisicaoCriacaoUsuarioDto;
import valhalla.core.stock.app.modules.users.dto.RespostaUsuarioDto;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.modules.users.security.UserAuthorizationService;
import valhalla.core.stock.app.shared.error.EmailAlreadyExistsException;
import valhalla.core.stock.app.shared.error.InvalidUserUpdateException;
import valhalla.core.stock.app.shared.error.ProfileNotFoundException;
import valhalla.core.stock.app.shared.error.UserDeletionConflictException;
import valhalla.core.stock.app.shared.logging.BusinessEventLogger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private UserAuthorizationService userAuthorizationService;
    @Mock private TokenStateService tokenStateService;
    @Mock private ServicoPermissoes servicoPermissoes;

    private UserService userService;
    private UUID establishmentId;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository, profileRepository, passwordEncoder,
                userAuthorizationService, tokenStateService, servicoPermissoes,
                new BusinessEventLogger()
        );
        establishmentId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none")
                .claim("establishmentId", establishmentId.toString()).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveCriarUsuarioComEmailNormalizadoESenhaCodificada() {
        Integer profileId = 1;
        UUID userId = UUID.randomUUID();
        ProfileEntity profile = ProfileEntity.builder()
                .id(profileId).name("Administrador")
                .establishment(EstablishmentEntity.builder().id(establishmentId).build()).build();
        RequisicaoCriacaoUsuarioDto request = new RequisicaoCriacaoUsuarioDto(
                "  Lucas Peres  ", "  LUCAS@EXEMPLO.COM  ", "11999999999", "senha-segura", "  Administrador  "
        );

        when(userRepository.existsByEmailIgnoreCase("lucas@exemplo.com")).thenReturn(false);
        when(profileRepository.findByEstablishment_IdAndNameIgnoreCase(any(), eq("Administrador")))
                .thenReturn(Optional.of(profile));
        when(passwordEncoder.encode("senha-segura")).thenReturn("senha-codificada");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });

        RespostaUsuarioDto response = userService.criarUsuario(request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        UserEntity savedUser = captor.getValue();

        assertAll(
                () -> assertEquals(userId, response.id()),
                () -> assertEquals("Lucas Peres", savedUser.getName()),
                () -> assertEquals("lucas@exemplo.com", savedUser.getEmail()),
                () -> assertEquals("11999999999", savedUser.getPhone()),
                () -> assertEquals("senha-codificada", savedUser.getPasswordHash()),
                () -> assertNotEquals(request.senha(), savedUser.getPasswordHash()),
                () -> assertTrue(savedUser.getActive())
        );
    }

    @Test
    void naoDeveCriarUsuarioQuandoEmailJaExistir() {
        RequisicaoCriacaoUsuarioDto request = requestValido();
        when(userRepository.existsByEmailIgnoreCase("lucas@exemplo.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> userService.criarUsuario(request));
        verifyNoInteractions(profileRepository, passwordEncoder);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void naoDeveCriarUsuarioQuandoPerfilNaoExistir() {
        RequisicaoCriacaoUsuarioDto request = requestValido();
        when(userRepository.existsByEmailIgnoreCase("lucas@exemplo.com")).thenReturn(false);
        when(profileRepository.findByEstablishment_IdAndNameIgnoreCase(any(), eq(request.nomePerfil())))
                .thenReturn(Optional.empty());

        assertThrows(ProfileNotFoundException.class, () -> userService.criarUsuario(request));
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveTratarConflitoDeEmailOcorridoDurantePersistencia() {
        RequisicaoCriacaoUsuarioDto request = requestValido();
        ProfileEntity profile = ProfileEntity.builder().id(2).name("Atendente")
                .establishment(EstablishmentEntity.builder().id(establishmentId).build()).build();

        when(userRepository.existsByEmailIgnoreCase("lucas@exemplo.com")).thenReturn(false);
        when(profileRepository.findByEstablishment_IdAndNameIgnoreCase(any(), eq("Atendente")))
                .thenReturn(Optional.of(profile));
        when(passwordEncoder.encode("senha-segura")).thenReturn("senha-codificada");
        when(userRepository.saveAndFlush(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException("e-mail duplicado"));

        assertThrows(EmailAlreadyExistsException.class, () -> userService.criarUsuario(request));
    }

    @Test
    void deveListarUsuariosDoEstabelecimento() {
        UserEntity u = usuarioEntidade(UUID.randomUUID(), "Operador", "op@meraki.com");
        when(userRepository.findAllByEstablishment_Id(eq(establishmentId), any(Sort.class)))
                .thenReturn(List.of(u));

        List<RespostaUsuarioDto> lista = userService.listarUsuarios();

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).nome()).isEqualTo("Operador");
    }

    @Test
    void deveBuscarUsuarioPorIdComSucesso() {
        UUID id = UUID.randomUUID();
        UserEntity u = usuarioEntidade(id, "Operador", "op@meraki.com");
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        RespostaUsuarioDto resposta = userService.buscarUsuario(id);

        assertThat(resposta.id()).isEqualTo(id);
    }

    @Test
    void deveFalharAoBuscarUsuarioInexistente() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> userService.buscarUsuario(id));
    }

    @Test
    void deveRejeitarAtualizacaoSemNenhumCampoValido() {
        UUID id = UUID.randomUUID();
        UserEntity u = usuarioEntidade(id, "Nome", "email@meraki.com");
        when(userRepository.findById(id)).thenReturn(Optional.of(u));

        assertThrows(InvalidUserUpdateException.class,
                () -> userService.atualizarUsuario(id, new RequisicaoAtualizacaoUsuarioDto(null, null, null, null, null, null)));

        assertThrows(InvalidUserUpdateException.class,
                () -> userService.atualizarUsuario(id, new RequisicaoAtualizacaoUsuarioDto("   ", null, null, null, null, null)));

        assertThrows(InvalidUserUpdateException.class,
                () -> userService.atualizarUsuario(id, new RequisicaoAtualizacaoUsuarioDto(null, "   ", null, null, null, null)));

        assertThrows(InvalidUserUpdateException.class,
                () -> userService.atualizarUsuario(id, new RequisicaoAtualizacaoUsuarioDto(null, null, null, null, "  ", null)));
    }

    @Test
    void deveBloquearNaoGerenteDeAlterarPerfilOuStatus() {
        UUID id = UUID.randomUUID();
        UserEntity u = usuarioEntidade(id, "Nome", "email@meraki.com");
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(userAuthorizationService.hasPermission(any(), eq("USUARIOS_EDITAR"))).thenReturn(false);

        RequisicaoAtualizacaoUsuarioDto dto = new RequisicaoAtualizacaoUsuarioDto(null, null, null, null, "Gerente", null);

        assertThrows(AccessDeniedException.class, () -> userService.atualizarUsuario(id, dto));
    }

    @Test
    void deveAtualizarDadosBasicosDoUsuario() {
        UUID id = UUID.randomUUID();
        UserEntity u = usuarioEntidade(id, "Nome Antigo", "antigo@meraki.com");
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(userAuthorizationService.hasPermission(any(), eq("USUARIOS_EDITAR"))).thenReturn(false);
        when(userRepository.saveAndFlush(u)).thenReturn(u);

        RequisicaoAtualizacaoUsuarioDto dto = new RequisicaoAtualizacaoUsuarioDto(" Novo Nome ", null, " 11988887777 ", null, null, null);
        RespostaUsuarioDto resposta = userService.atualizarUsuario(id, dto);

        assertThat(resposta.nome()).isEqualTo("Novo Nome");
        assertThat(u.getPhone()).isEqualTo("11988887777");
    }

    @Test
    void deveAtualizarSenhaEPerfilERevogarTokens() {
        UUID id = UUID.randomUUID();
        UserEntity u = usuarioEntidade(id, "Operador", "op@meraki.com");
        ProfileEntity perfilNovo = ProfileEntity.builder().id(2).name("Supervisor")
                .establishment(u.getEstablishment()).build();

        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        when(userAuthorizationService.hasPermission(any(), eq("USUARIOS_EDITAR"))).thenReturn(true);
        when(passwordEncoder.encode("nova-senha")).thenReturn("hash-novo");
        when(profileRepository.findByEstablishment_IdAndNameIgnoreCase(establishmentId, "Supervisor"))
                .thenReturn(Optional.of(perfilNovo));
        when(userRepository.saveAndFlush(u)).thenReturn(u);

        RequisicaoAtualizacaoUsuarioDto dto = new RequisicaoAtualizacaoUsuarioDto(
                null, null, null, "nova-senha", "Supervisor", false);

        userService.atualizarUsuario(id, dto);

        assertThat(u.getPasswordHash()).isEqualTo("hash-novo");
        assertThat(u.getProfile()).isEqualTo(perfilNovo);
        assertThat(u.getStatus()).isEqualTo(UserStatus.INATIVO);
        verify(servicoPermissoes).garantirAlteracaoUsuarioMantemAdministrador(u);
        verify(tokenStateService).revokeAll(id);
    }

    @Test
    void deveExcluirUsuarioExistente() {
        UserEntity user = usuarioEntidade(UUID.randomUUID(), "Usuário", "usuario@exemplo.com");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.deletarUsuario(user.getId());

        verify(servicoPermissoes).garantirUsuarioPodeSerDesativadoOuExcluido(user);
        verify(userRepository).delete(user);
        verify(userRepository).flush();
        verify(tokenStateService).revokeAll(user.getId());
    }

    @Test
    void naoDeveExcluirUsuarioInexistente() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> userService.deletarUsuario(userId)
        );

        assertEquals("Usuário não encontrado", exception.getMessage());
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deveInformarConflitoQuandoUsuarioPossuirVinculos() {
        UserEntity user = usuarioEntidade(UUID.randomUUID(), "Usuário", "usuario@exemplo.com");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        doThrow(new DataIntegrityViolationException("registro vinculado"))
                .when(userRepository)
                .flush();

        UserDeletionConflictException exception = assertThrows(
                UserDeletionConflictException.class,
                () -> userService.deletarUsuario(user.getId())
        );

        assertEquals(
                "Usuário não pode ser excluído porque possui registros vinculados",
                exception.getMessage()
        );
        verify(tokenStateService).revokeAll(user.getId());
    }

    private UserEntity usuarioEntidade(UUID id, String nome, String email) {
        ProfileEntity perfil = ProfileEntity.builder().id(1).name("Atendente")
                .establishment(EstablishmentEntity.builder().id(establishmentId).build()).build();
        return UserEntity.builder()
                .id(id)
                .name(nome)
                .email(email)
                .status(UserStatus.ATIVO)
                .profile(perfil)
                .establishment(perfil.getEstablishment())
                .passwordHash("hash")
                .build();
    }

    private RequisicaoCriacaoUsuarioDto requestValido() {
        return new RequisicaoCriacaoUsuarioDto(
                "Lucas Peres", "lucas@exemplo.com", null, "senha-segura", "Atendente"
        );
    }
}
