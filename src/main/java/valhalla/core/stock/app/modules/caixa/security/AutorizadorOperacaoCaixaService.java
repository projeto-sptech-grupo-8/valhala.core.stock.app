package valhalla.core.stock.app.modules.caixa.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import valhalla.core.stock.app.modules.accesscontrol.security.PermissionAuthorizationService;
import valhalla.core.stock.app.modules.accesscontrol.security.PermissionResolver;
import valhalla.core.stock.app.modules.caixa.exception.AutorizacaoCaixaNegadaException;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.entity.UserStatus;
import valhalla.core.stock.app.modules.users.repository.UserRepository;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AutorizadorOperacaoCaixaService {

    public record AutorizadorResolvido(UUID usuarioId, String nome) {
    }

    private final PermissionAuthorizationService permissionAuthorizationService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ContextoCaixa contextoCaixa;

    public AutorizadorOperacaoCaixaService(
            PermissionAuthorizationService permissionAuthorizationService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ContextoCaixa contextoCaixa
    ) {
        this.permissionAuthorizationService = permissionAuthorizationService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.contextoCaixa = contextoCaixa;
    }

    @Transactional(readOnly = true)
    public Optional<AutorizadorResolvido> tentarResolverAutorizador(
            String codigoPermissao,
            String autorizadorEmail,
            String autorizadorSenha
    ) {
        UUID estabelecimentoId = contextoCaixa.estabelecimentoId();
        boolean informouEmail = autorizadorEmail != null && !autorizadorEmail.isBlank();
        boolean informouSenha = autorizadorSenha != null && !autorizadorSenha.isBlank();

        if (informouEmail || informouSenha) {
            if (!informouEmail || !informouSenha) {
                throw new AutorizacaoCaixaNegadaException(
                        "Informe o e-mail e a senha do autorizador para aprovar a operação"
                );
            }
            UserEntity autorizador = userRepository.findByEmailIgnoreCase(autorizadorEmail.trim())
                    .filter(u -> u.getStatus() == UserStatus.ATIVO)
                    .filter(u -> u.getEstablishment() != null
                            && estabelecimentoId.equals(u.getEstablishment().getId()))
                    .orElseThrow(() -> new AutorizacaoCaixaNegadaException(
                            "Credenciais de autorização inválidas ou sem permissão para esta operação"
                    ));

            if (!passwordEncoder.matches(autorizadorSenha, autorizador.getPasswordHash())) {
                throw new AutorizacaoCaixaNegadaException(
                        "Credenciais de autorização inválidas ou sem permissão para esta operação"
                );
            }

            if (!usuarioPossuiPermissao(autorizador, codigoPermissao)) {
                throw new AutorizacaoCaixaNegadaException(
                        "O usuário informado não possui a permissão " + codigoPermissao
                );
            }

            return Optional.of(new AutorizadorResolvido(autorizador.getId(), autorizador.getName()));
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (permissionAuthorizationService.hasPermission(codigoPermissao, authentication)) {
            UUID usuarioId = contextoCaixa.usuarioId();
            String nome = userRepository.findById(usuarioId)
                    .map(UserEntity::getName)
                    .orElse("Operador");
            return Optional.of(new AutorizadorResolvido(usuarioId, nome));
        }

        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public AutorizadorResolvido exigirAutorizador(
            String codigoPermissao,
            String autorizadorEmail,
            String autorizadorSenha,
            String mensagemQuandoNegado
    ) {
        return tentarResolverAutorizador(codigoPermissao, autorizadorEmail, autorizadorSenha)
                .orElseThrow(() -> new AutorizacaoCaixaNegadaException(mensagemQuandoNegado));
    }

    private boolean usuarioPossuiPermissao(UserEntity usuario, String codigoPermissao) {
        if (usuario.getProfile() != null
                && "Gerente".equalsIgnoreCase(usuario.getProfile().getName())) {
            return true;
        }
        Set<String> permissoes = PermissionResolver.effectiveCodes(usuario);
        return permissoes.contains(codigoPermissao);
    }
}
