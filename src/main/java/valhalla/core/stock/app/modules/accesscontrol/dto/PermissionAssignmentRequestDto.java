package valhalla.core.stock.app.modules.accesscontrol.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record PermissionAssignmentRequestDto(@NotNull Set<Integer> functionalityIds) {
}
