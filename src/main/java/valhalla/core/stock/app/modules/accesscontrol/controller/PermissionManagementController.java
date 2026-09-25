package valhalla.core.stock.app.modules.accesscontrol.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import valhalla.core.stock.app.modules.accesscontrol.dto.*;
import valhalla.core.stock.app.modules.accesscontrol.service.PermissionManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/autorizacoes")
@RequiredArgsConstructor
@Tag(name = "Autorizações", description = "Perfis, funcionalidades e permissões individuais.")
@SecurityRequirement(name = "accessTokenCookie")
public class PermissionManagementController {
    private final PermissionManagementService permissionManagementService;

    @PutMapping("/perfis/{profileId}/funcionalidades")
    @Operation(summary = "Substituir permissões de perfil por IDs", deprecated = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERFIS_GERENCIAR', authentication)")
    public ResponseEntity<Void> replaceProfilePermissions(@PathVariable Integer profileId,
            @Valid @RequestBody PermissionAssignmentRequestDto request,
            Authentication authentication) {
        permissionManagementService.replaceProfilePermissions(profileId,
                request.functionalityIds(), authentication);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/usuarios/{userId}/funcionalidades")
    @Operation(summary = "Conceder permissões diretas por IDs", deprecated = true)
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERMISSOES_GERENCIAR', authentication)")
    public ResponseEntity<Void> replaceUserPermissions(@PathVariable UUID userId,
            @Valid @RequestBody PermissionAssignmentRequestDto request,
            Authentication authentication) {
        permissionManagementService.replaceUserPermissions(userId,
                request.functionalityIds(), authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/funcionalidades")
    @Operation(summary = "Listar funcionalidades disponíveis")
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERMISSOES_GERENCIAR', authentication)")
    public ResponseEntity<java.util.List<FunctionalityResponseDto>> listFunctionalities() {
        return ResponseEntity.ok(permissionManagementService.listAvailableFunctionalities());
    }

    @GetMapping("/perfis")
    @Operation(summary = "Listar perfis do estabelecimento")
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERFIS_GERENCIAR', authentication)")
    public ResponseEntity<java.util.List<ProfileResponseDto>> listProfiles(Authentication authentication) {
        return ResponseEntity.ok(permissionManagementService.listProfiles(authentication));
    }

    @GetMapping("/perfis/{profileId}")
    @Operation(summary = "Buscar perfil e suas funcionalidades")
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERFIS_GERENCIAR', authentication)")
    public ResponseEntity<ProfileResponseDto> getProfile(@PathVariable Integer profileId,
                                                           Authentication authentication) {
        return ResponseEntity.ok(permissionManagementService.getProfile(profileId, authentication));
    }

    @PostMapping("/perfis")
    @Operation(summary = "Criar perfil")
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERFIS_GERENCIAR', authentication)")
    public ResponseEntity<ProfileResponseDto> createProfile(@Valid @RequestBody ProfileRequestDto request,
                                                              Authentication authentication) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(permissionManagementService.createProfile(request, authentication));
    }

    @PatchMapping("/perfis/{profileId}")
    @Operation(summary = "Atualizar nome ou descrição do perfil")
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERFIS_GERENCIAR', authentication)")
    public ResponseEntity<ProfileResponseDto> updateProfile(@PathVariable Integer profileId,
            @Valid @RequestBody ProfileUpdateRequestDto request, Authentication authentication) {
        return ResponseEntity.ok(permissionManagementService.updateProfile(profileId, request, authentication));
    }

    @DeleteMapping("/perfis/{profileId}")
    @Operation(summary = "Excluir perfil sem usuários vinculados")
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERFIS_GERENCIAR', authentication)")
    public ResponseEntity<Void> deleteProfile(@PathVariable Integer profileId,
                                                Authentication authentication) {
        permissionManagementService.deleteProfile(profileId, authentication);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/perfis/{profileId}/funcionalidades/codigos")
    @Operation(summary = "Substituir funcionalidades de um perfil por códigos")
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERFIS_GERENCIAR', authentication)")
    public ResponseEntity<ProfileResponseDto> replaceProfileFunctionalities(
            @PathVariable Integer profileId, @RequestBody java.util.Set<String> codes,
            Authentication authentication) {
        return ResponseEntity.ok(permissionManagementService.replaceProfileFunctionalities(
                profileId, codes, authentication));
    }

    @PutMapping("/usuarios/{userId}/sobrescritas-permissao")
    @Operation(summary = "Substituir GRANTs e REVOKEs individuais do usuário")
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERMISSOES_GERENCIAR', authentication)")
    public ResponseEntity<Void> replaceUserPermissionOverrides(@PathVariable UUID userId,
            @Valid @RequestBody UserPermissionOverridesRequestDto request,
            Authentication authentication) {
        permissionManagementService.replaceUserPermissionOverrides(userId,
                request.overrides(), authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/usuarios/{userId}/permissoes")
    @Operation(summary = "Consultar permissões efetivas e suas origens")
    @PreAuthorize("@permissionAuthorizationService.hasPermission('PERMISSOES_GERENCIAR', authentication)")
    public ResponseEntity<UserPermissionsResponseDto> getUserPermissions(@PathVariable UUID userId,
            Authentication authentication) {
        return ResponseEntity.ok(permissionManagementService.getUserPermissions(userId, authentication));
    }
}
