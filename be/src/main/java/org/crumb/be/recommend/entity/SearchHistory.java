package org.crumb.be.recommend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "search_history", indexes = {
        @Index(name="idx_search_user_created", columnList = "userId, createdAt DESC")
})
public class SearchHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private Long userId;

    @Column(nullable=false, length=200)
    private String query;

    @Column
    private Double lat; // nullable

    @Column
    private Double lng; // nullable

    @CreationTimestamp
    @Column(nullable=false, updatable=false)
    private Instant createdAt;
}