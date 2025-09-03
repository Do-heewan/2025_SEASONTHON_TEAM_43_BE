package org.crumb.be.course.dto;

import jakarta.validation.constraints.*;

public record AddCourseSpotRequest(
        @NotNull Long bakeryId,
        @Size(max=200) String note
) {}
