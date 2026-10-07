package valhalla.core.stock.app.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token CSRF de uma sessão autenticada, usado apenas no header X-XSRF-TOKEN")
public record RespostaCsrfTokenDto(
        @Schema(example = "YxBq8vlPoA1pP2dM0R2N9Q") String token
) {
}
