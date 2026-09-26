package valhalla.core.stock.app.modules.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import valhalla.core.stock.app.modules.auth.entity.UserSessionEntity;
import valhalla.core.stock.app.modules.auth.repository.UserSessionRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.shared.error.InvalidRefreshTokenException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenStateServiceTest {

    @Mock private UserSessionRepository repositorioSessaoUsuario;

    @Test
    void deveAtualizarSessaoExistenteAoSubstituirTokens() {
        TokenStateService servicoEstadoToken = new TokenStateService(repositorioSessaoUsuario);
        UUID idUsuario = UUID.randomUUID();
        UserSessionEntity sessao = UserSessionEntity.builder().userId(idUsuario).build();
        UUID novoJtiAcesso = UUID.randomUUID();
        UUID novoJtiRefresh = UUID.randomUUID();
        Instant expiracao = Instant.parse("2030-01-01T00:00:00Z");
        when(repositorioSessaoUsuario.findById(idUsuario)).thenReturn(Optional.of(sessao));

        servicoEstadoToken.replace(idUsuario, novoJtiAcesso, novoJtiRefresh, expiracao,
                UserEntity.builder().id(idUsuario).build());

        assertEquals(novoJtiAcesso, sessao.getAccessJti());
        assertEquals(novoJtiRefresh, sessao.getRefreshJti());
        verify(repositorioSessaoUsuario, never()).save(any());
    }

    @Test
    void deveCriarSessaoQuandoUsuarioAindaNaoPossuiUma() {
        TokenStateService servicoEstadoToken = new TokenStateService(repositorioSessaoUsuario);
        UUID idUsuario = UUID.randomUUID();
        UserEntity usuario = UserEntity.builder().id(idUsuario).build();
        when(repositorioSessaoUsuario.findById(idUsuario)).thenReturn(Optional.empty());

        servicoEstadoToken.replace(idUsuario, UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2030-01-01T00:00:00Z"), usuario);

        ArgumentCaptor<UserSessionEntity> captor = ArgumentCaptor.forClass(UserSessionEntity.class);
        verify(repositorioSessaoUsuario).save(captor.capture());
        assertEquals(usuario, captor.getValue().getUser());
    }

    @Test
    void deveRejeitarRotacaoQuandoRefreshJaFoiUsado() {
        TokenStateService servicoEstadoToken = new TokenStateService(repositorioSessaoUsuario);
        when(repositorioSessaoUsuario.rotate(any(), any(), any(), any(), any())).thenReturn(0);

        assertThrows(InvalidRefreshTokenException.class,
                () -> servicoEstadoToken.rotate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), Instant.now().plusSeconds(60)));
    }
}
