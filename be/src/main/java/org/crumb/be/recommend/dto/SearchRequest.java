package org.crumb.be.recommend.dto;

import jakarta.validation.constraints.NotBlank;

public record SearchRequest(
        @NotBlank String query,
        Double lat,
        Double lng,
        Integer radius // m; null이면 기본값
) {}
