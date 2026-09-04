package valhalla.core.stock.app.modules.users.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;
import valhalla.core.stock.app.modules.accesscontrol.repository.ProfileRepository;
import valhalla.core.stock.app.modules.users.dto.UserCreateRequestDto;
import valhalla.core.stock.app.modules.users.dto.UserResponseDto;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.modules.users.security.UserAuthorizationService;
import valhalla.core.stock.app.shared.error.EmailAlreadyExistsException;
import valhalla.core.stock.app.shared.error.ProfileNotFoundException;
import valhalla.core.stock.app.shared.error.UserDeletionConflictException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserAuthorizationService userAuthorizationService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                profileRepository,
                passwordEncoder,
                userAuthorizationService
        );
    }

    @Test
    void deveCriarUsuarioComEmailNormalizadoESenhaCodificada() {
        UUID profileId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ProfileEntity profile = ProfileEntity.builder()
                .id(profileId)
                .name("Administrador")
                .build();
        UserCreateRequestDto request = new UserCreateRequestDto(
                "  Lucas Peres  ",
                "  LUCAS@EXEMPLO.COM  ",
                "11999999999",
                "senha-segura",
                "  Administrador  "
        );

        when(userRepository.existsByEmailIgnoreCase("lucas@exemplo.com")).thenReturn(false);
        when(profileRepository.findByNameIgnoreCase("Administrador"))
                .thenReturn(Optional.of(profile));
        when(passwordEncoder.encode("senha-segura")).thenReturn("senha-codificada");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(userId);
            return user;
        });

        UserResponseDto response = userService.criarUsuario(request);

        ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(captor.capture());
        UserEntity savedUser = captor.getValue();

        assertAll(
                () -> assertEquals(userId, response.id()),
                () -> assertEquals("Lucas Peres", savedUser.getName()),
                () -> assertEquals("lucas@exemplo.com", savedUser.getEmail()),
                () -> assertEquals("11999999999", savedUser.getPhone()),
                () -> assertEquals("senha-codificada", savedUser.getPasswordHash()),
                () -> assertNotEquals(request.password(), savedUser.getPasswordHash()),
                () -> assertTrue(savedUser.getActive())
        );
    }

    @Test
    void naoDeveCriarUsuarioQuandoEmailJaExistir() {
        UserCreateRequestDto request = requestValido();
        when(userRepository.existsByEmailIgnoreCase("lucas@exemplo.com")).thenReturn(true);

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> userService.criarUsuario(request)
        );

        verifyNoInteractions(profileRepository, passwordEncoder);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void naoDeveCriarUsuarioQuandoPerfilNaoExistir() {
        UserCreateRequestDto request = requestValido();
        when(userRepository.existsByEmailIgnoreCase("lucas@exemplo.com")).thenReturn(false);
        when(profileRepository.findByNameIgnoreCase(request.profileName()))
                .thenReturn(Optional.empty());

        assertThrows(
                ProfileNotFoundException.class,
                () -> userService.criarUsuario(request)
        );

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void deveTratarConflitoDeEmailOcorridoDurantePersistencia() {
        UserCreateRequestDto request = requestValido();
        ProfileEntity profile = ProfileEntity.builder()
                .id(UUID.randomUUID())
                .name("Atendente")
                .build();

        when(userRepository.existsByEmailIgnoreCase("lucas@exemplo.com"))
                .thenReturn(false);
        when(profileRepository.findByNameIgnoreCase("Atendente"))
                .thenReturn(Optional.of(profile));
        when(passwordEncoder.encode("senha-segura")).thenReturn("senha-codificada");
        when(userRepository.saveAndFlush(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException("e-mail duplicado"));

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> userService.criarUsuario(request)
        );
    }

    @Test
    void deveExcluirUsuarioExistente() {
        UserEntity user = UserEntity.builder()
                .id(UUID.randomUUID())
                .name("Usuário")
                .email("usuario@exemplo.com")
                .build();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        userService.deletarUsuario(user.getId());

        verify(userRepository).delete(user);
        verify(userRepository).flush();
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
        UserEntity user = UserEntity.builder()
                .id(UUID.randomUUID())
                .name("Usuário")
                .email("usuario@exemplo.com")
                .build();
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
    }

    private UserCreateRequestDto requestValido() {
        return new UserCreateRequestDto(
                "Lucas Peres",
                "lucas@exemplo.com",
                null,
                "senha-segura",
                "Atendente"
        );
    }
}
