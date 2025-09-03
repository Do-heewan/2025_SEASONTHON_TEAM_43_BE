package org.crumb.be.course.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record CreateCourseRequest(
        @NotBlank @Size(min=2, max=200) String title,
        @Size(max=1000) String description,
        Boolean isPublic,
        List<Long> bakeryIds   // 초기 스팟 등록용(옵션)
) {}
