package valhalla.core.stock.app.modules.auth.dto;

public record TokenResponseDto (
        String acessToken,
        String refreshToken,
        String username
){}
