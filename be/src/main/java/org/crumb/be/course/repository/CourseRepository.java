package org.crumb.be.course.repository;

import org.crumb.be.course.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {
    // 내 코스(공개/비공개 모두)
    List<Course> findAllByAuthorIdOrderByCreatedAtDesc(Long authorId);
    // 공개 코스 중, 특정 사용자 제외
    List<Course> findAllByIsPublicTrueAndAuthorIdNotOrderByCreatedAtDesc(Long excludedAuthorId);
}
