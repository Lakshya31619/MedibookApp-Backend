package com.medibook.review.service;

import com.medibook.review.dto.ReviewDto.*;
import com.medibook.review.entity.Review;

import java.util.List;

public interface ReviewService {

    Review addReview(AddReviewRequest request);

    Review getReviewById(int reviewId);

    Review getByAppointment(int appointmentId);

    List<Review> getByProvider(int providerId);

    List<Review> getByPatient(int patientId);

    List<Review> getAllReviews();

    List<Review> getFlaggedReviews();

    Review updateReview(int reviewId, UpdateReviewRequest request);

    void deleteReview(int reviewId);

    void flagReview(int reviewId, String reason);

    void unflagReview(int reviewId);

    void verifyReview(int reviewId);

    double getAvgRating(int providerId);

    int getReviewCount(int providerId);

    RatingSummary getRatingSummary(int providerId);
}