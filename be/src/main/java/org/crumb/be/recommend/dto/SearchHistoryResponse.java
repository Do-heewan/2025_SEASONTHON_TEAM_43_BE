package org.crumb.be.recommend.dto;

public record SearchHistoryResponse(
        Long id, String query, Double lat, Double lng, java.time.Instant createdAt
) {}
