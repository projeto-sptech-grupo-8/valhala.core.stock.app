package valhalla.core.stock.app.modules.accesscontrol.dto;

import java.util.Set;

public record ProfileResponseDto(Integer id, String name, String description,
                                 Set<String> functionalityCodes) { }
