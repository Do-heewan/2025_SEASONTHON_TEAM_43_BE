package org.crumb.be.course.dto;

import java.time.Instant;
import java.util.List;

public record CourseResponse(
        Long id,
        String title,
        String description,
        boolean isPublic,
        long viewCount,
        Instant createdAt,
        Instant updatedAt,
        List<CourseSpotResponse> spots
) {}
