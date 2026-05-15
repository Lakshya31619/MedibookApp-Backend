package com.medibook.review.resource;

import com.medibook.review.dto.ReviewDto.*;
import com.medibook.review.entity.Review;
import com.medibook.review.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reviews")
public class ReviewResource {

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${auth.service.url}")
    private String authServiceUrl;

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<PublicReview>> getByProvider(@PathVariable int providerId) {
        return ResponseEntity.ok(
            reviewService.getByProvider(providerId)
                .stream().map(this::toPublicReview).toList()
        );
    }

    @GetMapping("/rating/{providerId}")
    public ResponseEntity<?> getAvgRating(@PathVariable int providerId) {
        return ResponseEntity.ok(Map.of(
            "providerId", providerId,
            "avgRating",  reviewService.getAvgRating(providerId),
            "totalReviews", reviewService.getReviewCount(providerId)
        ));
    }

    @GetMapping("/summary/{providerId}")
    public ResponseEntity<RatingSummary> getSummary(@PathVariable int providerId) {
        return ResponseEntity.ok(reviewService.getRatingSummary(providerId));
    }

    @PostMapping
    public ResponseEntity<?> addReview(@Valid @RequestBody AddReviewRequest request) {
        try {
            Review review = reviewService.addReview(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(toResponse(review));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/appointment/{appointmentId}")
    public ResponseEntity<?> getByAppointment(@PathVariable int appointmentId) {
        try {
            return ResponseEntity.ok(
                toResponse(reviewService.getByAppointment(appointmentId)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<ReviewResponse>> getByPatient(@PathVariable int patientId) {
        return ResponseEntity.ok(
            reviewService.getByPatient(patientId)
                .stream().map(this::toResponse).toList()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable int id,
                                     @RequestBody UpdateReviewRequest request) {
        try {
            return ResponseEntity.ok(
                toResponse(reviewService.updateReview(id, request)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/flag")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> flag(@PathVariable int id,
                                   @Valid @RequestBody FlagReviewRequest request) {
        try {
            reviewService.flagReview(id, request.getFlagReason());
            return ResponseEntity.ok(Map.of(
                "message", "Review flagged for moderation",
                "reviewId", id
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/unflag")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> unflag(@PathVariable int id) {
        try {
            reviewService.unflagReview(id);
            return ResponseEntity.ok(Map.of("message", "Review unflagged", "reviewId", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> verify(@PathVariable int id) {
        try {
            reviewService.verifyReview(id);
            return ResponseEntity.ok(Map.of("message", "Review verified", "reviewId", id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReviewResponse>> getAll() {
        return ResponseEntity.ok(
            reviewService.getAllReviews()
                .stream().map(this::toResponse).toList()
        );
    }

    @GetMapping("/flagged")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ReviewResponse>> getFlagged() {
        return ResponseEntity.ok(
            reviewService.getFlaggedReviews()
                .stream().map(this::toResponse).toList()
        );
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable int id) {
        try {
            reviewService.deleteReview(id);
            return ResponseEntity.ok(Map.of(
                "message", "Review deleted and provider rating recomputed"
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private ReviewResponse toResponse(Review r) {
        ReviewResponse res = new ReviewResponse();
        res.setReviewId(r.getReviewId());
        res.setAppointmentId(r.getAppointmentId());
        res.setPatientId(r.getPatientId());
        res.setProviderId(r.getProviderId());
        res.setRating(r.getRating());
        res.setComment(r.getComment());
        res.setReviewDate(r.getReviewDate() != null ? r.getReviewDate().toString() : "");
        res.setVerified(r.isVerified());
        res.setAnonymous(r.isAnonymous());
        res.setFlagged(r.isFlagged());
        res.setFlagReason(r.getFlagReason());
        res.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");
        return res;
    }

    private PublicReview toPublicReview(Review r) {
        PublicReview pr = new PublicReview();
        pr.setReviewId(r.getReviewId());

        String label;
        if (r.isAnonymous()) {
            label = "Anonymous";
        } else {
            label = fetchPatientName(r.getPatientId());
        }
        pr.setPatientLabel(label);

        pr.setRating(r.getRating());
        pr.setComment(r.getComment());
        pr.setReviewDate(r.getReviewDate() != null ? r.getReviewDate().toString() : "");
        pr.setVerified(r.isVerified());
        return pr;
    }

    @SuppressWarnings("unchecked")
    private String fetchPatientName(int patientId) {
        try {
            String url = authServiceUrl + "/auth/internal/users/" + patientId;
            Map<String, Object> userInfo = restTemplate.getForObject(url, Map.class);
            if (userInfo != null && userInfo.get("fullName") != null) {
                return (String) userInfo.get("fullName");
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not fetch patient name for id "
                + patientId + ": " + e.getMessage());
        }
        return "Patient #" + patientId;
    }
}