package com.medibook.review.serviceimpl;

import com.medibook.review.dto.ReviewDto.*;
import com.medibook.review.entity.Review;
import com.medibook.review.repository.ReviewRepository;
import com.medibook.review.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${provider.service.url}")
    private String providerServiceUrl;

    @Override
    @Transactional
    public Review addReview(AddReviewRequest request) {

        if (reviewRepository.existsByAppointmentId(request.getAppointmentId())) {
            throw new RuntimeException(
                "You have already submitted a review for this appointment.");
        }

        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new RuntimeException("Rating must be between 1 and 5.");
        }

        Review review = new Review();
        review.setAppointmentId(request.getAppointmentId());
        review.setPatientId(request.getPatientId());
        review.setProviderId(request.getProviderId());
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setAnonymous(request.isAnonymous());
        review.setVerified(false);
        review.setFlagged(false);

        Review saved = reviewRepository.save(review);

        pushRatingToProviderService(request.getProviderId());

        return saved;
    }

    @Override
    public Review getReviewById(int reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException(
                    "Review not found with id: " + reviewId));
    }

    @Override
    public Review getByAppointment(int appointmentId) {
        return reviewRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new RuntimeException(
                    "No review found for appointmentId: " + appointmentId));
    }

    @Override
    public List<Review> getByProvider(int providerId) {
        return reviewRepository.findByProviderIdOrderByReviewDateDesc(providerId);
    }

    @Override
    public List<Review> getByPatient(int patientId) {
        return reviewRepository.findByPatientId(patientId);
    }

    @Override
    public List<Review> getAllReviews() {
        return reviewRepository.findAll();
    }

    @Override
    public List<Review> getFlaggedReviews() {
        return reviewRepository.findByIsFlaggedOrderByCreatedAtDesc(true);
    }

    @Override
    @Transactional
    public Review updateReview(int reviewId, UpdateReviewRequest request) {
        Review review = getReviewById(reviewId);

        if (request.getRating() != null) {
            if (request.getRating() < 1 || request.getRating() > 5) {
                throw new RuntimeException("Rating must be between 1 and 5.");
            }
            review.setRating(request.getRating());
        }
        if (request.getComment() != null) {
            review.setComment(request.getComment());
        }
        if (request.getIsAnonymous() != null) {
            review.setAnonymous(request.getIsAnonymous());
        }

        review.setVerified(false);

        Review updated = reviewRepository.save(review);

        pushRatingToProviderService(review.getProviderId());

        return updated;
    }

    @Override
    @Transactional
    public void deleteReview(int reviewId) {
        Review review = getReviewById(reviewId);
        int providerId = review.getProviderId();

        reviewRepository.deleteByReviewId(reviewId);

        pushRatingToProviderService(providerId);
    }

    @Override
    @Transactional
    public void flagReview(int reviewId, String reason) {
        Review review = getReviewById(reviewId);
        review.setFlagged(true);
        review.setFlagReason(reason);
        reviewRepository.save(review);
    }

    @Override
    @Transactional
    public void unflagReview(int reviewId) {
        Review review = getReviewById(reviewId);
        review.setFlagged(false);
        review.setFlagReason(null);
        reviewRepository.save(review);
    }

    @Override
    @Transactional
    public void verifyReview(int reviewId) {
        Review review = getReviewById(reviewId);
        review.setVerified(true);
        review.setFlagged(false);
        reviewRepository.save(review);
    }

    @Override
    public double getAvgRating(int providerId) {
        return reviewRepository.avgRatingByProviderId(providerId);
    }

    @Override
    public int getReviewCount(int providerId) {
        return reviewRepository.countByProviderId(providerId);
    }

    @Override
    public RatingSummary getRatingSummary(int providerId) {
        double avg   = reviewRepository.avgRatingByProviderId(providerId);
        int    total = reviewRepository.countByProviderId(providerId);

        Map<Integer, Long> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) distribution.put(i, 0L);

        reviewRepository.getRatingDistribution(providerId)
                .forEach(row -> distribution.put(
                    ((Number) row[0]).intValue(),
                    ((Number) row[1]).longValue()
                ));

        RatingSummary summary = new RatingSummary();
        summary.setProviderId(providerId);
        summary.setAvgRating(Math.round(avg * 10.0) / 10.0);
        summary.setTotalReviews(total);
        summary.setDistribution(distribution);
        return summary;
    }

    private void pushRatingToProviderService(int providerId) {
        try {
            double newAvg = reviewRepository.avgRatingByProviderId(providerId);
            double rounded = Math.round(newAvg * 100.0) / 100.0;

            restTemplate.put(
                providerServiceUrl + "/providers/" + providerId
                + "/rating?value=" + rounded,
                null
            );

            System.out.println("Rating pushed to provider-service: provider="
                + providerId + " avgRating=" + rounded);

        } catch (Exception e) {
            System.err.println("Warning: Could not push rating to provider-service: "
                + e.getMessage());
        }
    }
}