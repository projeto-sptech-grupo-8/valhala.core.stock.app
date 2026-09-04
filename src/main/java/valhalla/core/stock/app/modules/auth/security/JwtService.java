package valhalla.core.stock.app.modules.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import valhalla.core.stock.app.modules.auth.config.JwtProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public String generateToken(Authentication authentication, UUID userId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.expiration());

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> authority.substring("ROLE_".length()))
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(authentication.getName())
                .id(UUID.randomUUID().toString())
                .claim("userId", userId.toString())
                .claim("roles", roles)
                .claim("purpose", "access")
                .build();

        return encodeToken(claims);
    }

    public String generateRefreshToken(Authentication authentication, UUID userId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.refreshExpiration());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(authentication.getName())
                .id(UUID.randomUUID().toString())
                .claim("userId", userId.toString())
                .claim("purpose", "refresh")
                .build();

        return encodeToken(claims);
    }

    private String encodeToken(JwtClaimsSet claimsSet) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder
                .encode(JwtEncoderParameters.from(header, claimsSet))
                .getTokenValue();
    }
}
