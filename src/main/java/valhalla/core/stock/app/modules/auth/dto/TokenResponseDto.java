package valhalla.core.stock.app.modules.auth.dto;

public record TokenResponseDto (
        String accessToken,
        String refreshToken,
        String username
){}
