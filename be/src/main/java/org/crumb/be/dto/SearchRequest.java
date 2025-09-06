package org.crumb.be.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SearchRequest (
    @NotNull Double lat,
    @NotNull
    Double lng,
    @Min(100) @Max(50000) Integer radius, // 미터
    Integer limit,
    String sort  // distance|rating
) { }
