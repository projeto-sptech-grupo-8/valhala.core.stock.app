package valhalla.core.stock.app.modules.estoque.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import valhalla.core.stock.app.modules.estoque.dto.IngredienteDrinkResponseDto;
import valhalla.core.stock.app.modules.estoque.dto.ReceitaDrinkRequestDto;
import valhalla.core.stock.app.modules.estoque.service.ReceitaDrinkService;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/drinks/{idDrink}/receita")
@Tag(name = "Receitas de drinks", description = "Ingredientes rastreáveis dos drinks do estabelecimento autenticado.")
@SecurityRequirement(name = "accessTokenCookie")
public class ReceitaDrinkController {

    private final ReceitaDrinkService receitaDrinkService;

    public ReceitaDrinkController(ReceitaDrinkService receitaDrinkService) {
        this.receitaDrinkService = receitaDrinkService;
    }

    @GetMapping
    @Operation(summary = "Consultar a receita de um drink")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receita retornada."),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou drink de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Drink não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<IngredienteDrinkResponseDto>> buscarReceita(@PathVariable UUID idDrink) {
        return ResponseEntity.ok(receitaDrinkService.buscarReceita(idDrink));
    }

    @PutMapping
    @SecurityRequirement(name = "csrfTokenHeader")
    @Operation(summary = "Substituir integralmente a receita de um drink")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receita atualizada."),
            @ApiResponse(responseCode = "400", description = "Dados ou regras da receita inválidos.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou produto de outro estabelecimento.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Drink ou ingrediente não encontrado.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<IngredienteDrinkResponseDto>> substituirReceita(
            @PathVariable UUID idDrink,
            @Valid @RequestBody ReceitaDrinkRequestDto requestDto
    ) {
        return ResponseEntity.ok(receitaDrinkService.substituirReceita(idDrink, requestDto));
    }
}
