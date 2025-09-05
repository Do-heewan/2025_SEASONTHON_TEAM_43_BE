package org.crumb.be.recommend.dto;

public record RecommendedBakery(
        Long id,
        String name,
        String address,
        Double lat,
        Double lng,
        String intro,
        Double distance,
        Double score
) {}