package valhalla.core.stock.app.modules.accesscontrol.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import valhalla.core.stock.app.modules.accesscontrol.dto.PermissionAssignmentRequestDto;
import valhalla.core.stock.app.modules.accesscontrol.service.PermissionManagementService;

import java.util.UUID;

@RestController
@RequestMapping("/autorizacoes")
@RequiredArgsConstructor
public class PermissionManagementController {
    private final PermissionManagementService permissionManagementService;

    @PutMapping("/perfis/{profileId}/funcionalidades")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<Void> replaceProfilePermissions(@PathVariable Integer profileId,
            @Valid @RequestBody PermissionAssignmentRequestDto request,
            Authentication authentication) {
        permissionManagementService.replaceProfilePermissions(profileId,
                request.functionalityIds(), authentication);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/usuarios/{userId}/funcionalidades")
    @PreAuthorize("hasRole('GERENTE')")
    public ResponseEntity<Void> replaceUserPermissions(@PathVariable UUID userId,
            @Valid @RequestBody PermissionAssignmentRequestDto request,
            Authentication authentication) {
        permissionManagementService.replaceUserPermissions(userId,
                request.functionalityIds(), authentication);
        return ResponseEntity.noContent().build();
    }
}
