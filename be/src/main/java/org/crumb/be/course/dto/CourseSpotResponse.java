package org.crumb.be.course.dto;

import java.time.Instant;

public record CourseSpotResponse(
        Long spotId,
        Long bakeryId,
        String note,
        Instant createdAt
) {}
