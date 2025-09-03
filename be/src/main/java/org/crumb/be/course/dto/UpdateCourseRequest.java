package org.crumb.be.course.dto;

import jakarta.validation.constraints.*;

public record UpdateCourseRequest(
        @NotBlank @Size(min=2, max=200) String title,
        @Size(max=1000) String description,
        Boolean isPublic
) {}
