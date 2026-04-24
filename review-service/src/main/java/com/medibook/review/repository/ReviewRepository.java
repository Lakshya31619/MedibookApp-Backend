package com.medibook.review.repository;

import com.medibook.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Integer> {

    List<Review> findByProviderId(int providerId);

    List<Review> findByPatientId(int patientId);

    Optional<Review> findByAppointmentId(int appointmentId);

    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r " +
           "WHERE r.providerId = :providerId")
    double avgRatingByProviderId(@Param("providerId") int providerId);

    List<Review> findByProviderIdAndRating(int providerId, int rating);

    int countByProviderId(int providerId);

    boolean existsByAppointmentId(int appointmentId);

    void deleteByReviewId(int reviewId);

    List<Review> findByIsFlaggedOrderByCreatedAtDesc(boolean isFlagged);

    List<Review> findByProviderIdAndIsVerifiedOrderByReviewDateDesc(
            int providerId, boolean isVerified);

    List<Review> findByProviderIdOrderByReviewDateDesc(int providerId);

    @Query("SELECT r.rating, COUNT(r) FROM Review r " +
           "WHERE r.providerId = :providerId " +
           "GROUP BY r.rating ORDER BY r.rating DESC")
    List<Object[]> getRatingDistribution(@Param("providerId") int providerId);
}