package valhalla.core.stock.app.modules.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.users.entity.UserEntity;
import valhalla.core.stock.app.modules.users.repository.UserRepository;
import valhalla.core.stock.app.modules.accesscontrol.security.PermissionResolver;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Collection;
import java.util.LinkedHashSet;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        UserEntity user = userRepository
                .findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));

        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authoritiesFor(user))
                .disabled(!user.isActive())
                .accountLocked(user.getLockedUntil() != null
                        && user.getLockedUntil().isAfter(LocalDateTime.now()))
                .build();
    }

    public static Collection<SimpleGrantedAuthority> authoritiesFor(UserEntity user) {
        LinkedHashSet<String> authorities = new LinkedHashSet<>();
        authorities.add("ROLE_" + normalizeRole(user.getProfile().getName()));
        authorities.addAll(PermissionResolver.effectiveCodes(user));
        return authorities.stream().map(SimpleGrantedAuthority::new).toList();
    }

    public static String normalizeRole(String profileName) {
        String withoutAccents = Normalizer.normalize(profileName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return withoutAccents
                .trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
