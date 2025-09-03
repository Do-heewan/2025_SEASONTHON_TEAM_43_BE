package org.crumb.be.course.repository;

import org.crumb.be.course.entity.CourseSpot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseSpotRepository extends JpaRepository<CourseSpot, Long> {
    List<CourseSpot> findByCourseIdOrderByCreatedAtAsc(Long courseId);
    long countByCourseId(Long courseId);
    boolean existsByCourseIdAndBakeryId(Long courseId, Long bakeryId);
}