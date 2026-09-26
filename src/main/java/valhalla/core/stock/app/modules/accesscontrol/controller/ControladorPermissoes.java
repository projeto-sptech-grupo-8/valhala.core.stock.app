package valhalla.core.stock.app.modules.accesscontrol.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import valhalla.core.stock.app.modules.accesscontrol.dto.*;
import valhalla.core.stock.app.modules.accesscontrol.service.ServicoPermissoes;
import valhalla.core.stock.app.shared.exceptionhandler.ApiErrorResponse;

import java.util.UUID;

@RestController
@RequestMapping("/autorizacoes")
@RequiredArgsConstructor
@Tag(name = "Autorizações", description = "Perfis, funcionalidades e permissões individuais.")
@SecurityRequirement(name = "accessTokenCookie")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Sessão ausente, inválida ou expirada.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Usuário sem permissão ou de outro estabelecimento.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class ControladorPermissoes {
    private final ServicoPermissoes servicoPermissoes;

    @GetMapping("/funcionalidades")
    @Operation(summary = "Listar funcionalidades disponíveis")
    @ApiResponse(responseCode = "200", description = "Funcionalidades retornadas.")
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERMISSOES_GERENCIAR', authentication)")
    public ResponseEntity<java.util.List<RespostaFuncionalidadeDto>> listarFuncionalidades() {
        return ResponseEntity.ok(servicoPermissoes.listarFuncionalidadesDisponiveis());
    }

    @GetMapping("/perfis")
    @Operation(summary = "Listar perfis do estabelecimento")
    @ApiResponse(responseCode = "200", description = "Perfis retornados.")
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERFIS_GERENCIAR', authentication)")
    public ResponseEntity<java.util.List<RespostaPerfilDto>> listarPerfis(Authentication autenticacao) {
        return ResponseEntity.ok(servicoPermissoes.listarPerfis(autenticacao));
    }

    @GetMapping("/perfis/{profileId}")
    @Operation(summary = "Buscar perfil e suas funcionalidades")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil retornado."),
            @ApiResponse(responseCode = "404", description = "Perfil não encontrado.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERFIS_GERENCIAR', authentication)")
    public ResponseEntity<RespostaPerfilDto> buscarPerfil(@PathVariable Integer profileId,
                                                           Authentication autenticacao) {
        return ResponseEntity.ok(servicoPermissoes.buscarPerfil(profileId, autenticacao));
    }

    @PostMapping("/perfis")
    @Operation(summary = "Criar perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Perfil criado."),
            @ApiResponse(responseCode = "400", description = "Body inválido.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Já existe perfil com esse nome.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERFIS_GERENCIAR', authentication)")
    public ResponseEntity<RespostaPerfilDto> criarPerfil(@Valid @RequestBody RequisicaoPerfilDto requisicao,
                                                           Authentication autenticacao) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(servicoPermissoes.criarPerfil(requisicao, autenticacao));
    }

    @PatchMapping("/perfis/{profileId}")
    @Operation(summary = "Atualizar nome ou descrição do perfil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil atualizado."),
            @ApiResponse(responseCode = "400", description = "Body inválido ou sem campos para atualizar.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil não encontrado.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Já existe perfil com esse nome.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERFIS_GERENCIAR', authentication)")
    public ResponseEntity<RespostaPerfilDto> atualizarPerfil(@PathVariable Integer profileId,
            @Valid @RequestBody RequisicaoAtualizacaoPerfilDto requisicao, Authentication autenticacao) {
        return ResponseEntity.ok(servicoPermissoes.atualizarPerfil(profileId, requisicao, autenticacao));
    }

    @DeleteMapping("/perfis/{profileId}")
    @Operation(summary = "Excluir perfil sem usuários vinculados")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Perfil excluído."),
            @ApiResponse(responseCode = "404", description = "Perfil não encontrado.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Perfil possui usuários vinculados.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERFIS_GERENCIAR', authentication)")
    public ResponseEntity<Void> excluirPerfil(@PathVariable Integer profileId,
                                                Authentication autenticacao) {
        servicoPermissoes.excluirPerfil(profileId, autenticacao);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/perfis/{profileId}/funcionalidades/codigos")
    @Operation(summary = "Substituir funcionalidades de um perfil por códigos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Funcionalidades substituídas."),
            @ApiResponse(responseCode = "400", description = "Body inválido.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Perfil ou funcionalidade não encontrado.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A alteração removeria o último usuário apto a criar usuários.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERFIS_GERENCIAR', authentication)")
    public ResponseEntity<RespostaPerfilDto> substituirFuncionalidadesPerfil(
            @PathVariable Integer profileId, @RequestBody java.util.Set<String> codigos,
            Authentication autenticacao) {
        return ResponseEntity.ok(servicoPermissoes.substituirFuncionalidadesPerfil(
                profileId, codigos, autenticacao));
    }

    @PutMapping("/usuarios/{userId}/sobrescritas-permissao")
    @Operation(summary = "Substituir GRANTs e REVOKEs individuais do usuário")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sobrescritas substituídas."),
            @ApiResponse(responseCode = "400", description = "Body inválido ou código repetido.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Usuário ou funcionalidade não encontrado.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A alteração removeria o último usuário apto a criar usuários.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERMISSOES_GERENCIAR', authentication)")
    public ResponseEntity<Void> substituirSobrescritasPermissaoUsuario(@PathVariable UUID userId,
            @Valid @RequestBody RequisicaoSobrescritasPermissaoUsuarioDto requisicao,
            Authentication autenticacao) {
        servicoPermissoes.substituirSobrescritasPermissaoUsuario(userId,
                requisicao.sobrescritas(), autenticacao);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/usuarios/{userId}/permissoes")
    @Operation(summary = "Consultar permissões efetivas e suas origens")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Permissões retornadas."),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado.", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERMISSOES_GERENCIAR', authentication)")
    public ResponseEntity<RespostaPermissoesUsuarioDto> buscarPermissoesUsuario(@PathVariable UUID userId,
            Authentication autenticacao) {
        return ResponseEntity.ok(servicoPermissoes.buscarPermissoesUsuario(userId, autenticacao));
    }
}
