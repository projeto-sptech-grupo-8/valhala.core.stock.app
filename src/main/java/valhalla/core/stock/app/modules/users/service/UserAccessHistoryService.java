package valhalla.core.stock.app.modules.users.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.users.dto.RespostaHistoricoAcessoDto;
import valhalla.core.stock.app.modules.users.entity.TipoEventoAcessoUsuario;
import valhalla.core.stock.app.modules.users.entity.UserAccessHistoryEntity;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserAccessHistoryRepository;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAccessHistoryService {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_TENTATIVAS_SEM_BLOQUEIO = 5;
    private static final Duration DURACAO_BLOQUEIO = Duration.ofMinutes(15);

    private final UserAccessHistoryRepository userAccessHistoryRepository;
    private final UserRepository userRepository;
    private final RequestAccessMetadataExtractor metadataExtractor;

    @Transactional
    public void registrarLoginSucesso(UserEntity user) {
        user.setLastLoginAt(LocalDateTime.now());
        user.setLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        registrarEvento(user, TipoEventoAcessoUsuario.LOGIN_SUCESSO);
    }

    @Transactional
    public void registrarSenhaIncorreta(String emailNormalizado) {
        userRepository.findByEmailIgnoreCase(emailNormalizado).ifPresent(user -> {
            LocalDateTime agora = LocalDateTime.now();
            int attempts = user.getLoginAttempts() == null ? 0 : user.getLoginAttempts();
            if (user.getLockedUntil() != null && !user.getLockedUntil().isAfter(agora)) {
                attempts = 0;
                user.setLockedUntil(null);
            }
            int novasTentativas = attempts + 1;
            user.setLoginAttempts(novasTentativas);
            if (novasTentativas >= MAX_TENTATIVAS_SEM_BLOQUEIO) {
                user.setLockedUntil(agora.plus(DURACAO_BLOQUEIO));
            }
            userRepository.save(user);
            registrarEvento(user, TipoEventoAcessoUsuario.SENHA_INCORRETA);
        });
    }

    @Transactional
    public void registrarSenhaAlterada(UserEntity user) {
        registrarEvento(user, TipoEventoAcessoUsuario.SENHA_ALTERADA);
    }

    @Transactional
    @PreAuthorize("@userAuthorizationService.canView(#idUsuario, authentication)")
    public List<RespostaHistoricoAcessoDto> listarUltimosAcessos(UUID idUsuario, int limite) {
        if (limite < MIN_LIMIT || limite > MAX_LIMIT) {
            throw new IllegalArgumentException("O limite deve estar entre 1 e 50");
        }
        if (!userRepository.existsById(idUsuario)) {
            throw new EntityNotFoundException("Usuário não encontrado");
        }

        return userAccessHistoryRepository
                .findByUser_IdOrderByOccurredAtDescIdDesc(idUsuario, PageRequest.of(0, limite))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void registrarEvento(UserEntity user, TipoEventoAcessoUsuario tipoEvento) {
        RequestAccessMetadataExtractor.AccessMetadata metadata = metadataExtractor.extractCurrent();
        UserAccessHistoryEntity entry = UserAccessHistoryEntity.builder()
                .user(user)
                .establishment(user.getEstablishment())
                .eventType(tipoEvento)
                .sourceIp(metadata.sourceIp())
                .device(metadata.device())
                .userAgent(metadata.userAgent())
                .occurredAt(LocalDateTime.now())
                .build();
        userAccessHistoryRepository.save(entry);
    }

    private RespostaHistoricoAcessoDto toResponse(UserAccessHistoryEntity entity) {
        return new RespostaHistoricoAcessoDto(
                entity.getId(),
                entity.getEventType(),
                entity.getSourceIp(),
                entity.getDevice(),
                entity.getOccurredAt()
        );
    }
}
