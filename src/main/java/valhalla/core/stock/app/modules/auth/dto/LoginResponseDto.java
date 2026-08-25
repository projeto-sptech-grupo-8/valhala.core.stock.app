package valhalla.core.stock.app.modules.auth.dto;

public record LoginResponseDto(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
