package com.medibook.review;

import com.medibook.review.dto.ReviewDto.*;
import com.medibook.review.entity.Review;
import com.medibook.review.repository.ReviewRepository;
import com.medibook.review.serviceimpl.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private Review testReview;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reviewService,
            "providerServiceUrl", "http://localhost:8082");

        testReview = new Review();
        testReview.setReviewId(1);
        testReview.setAppointmentId(5);
        testReview.setPatientId(2);
        testReview.setProviderId(1);
        testReview.setRating(4);
        testReview.setComment("Good doctor");
        testReview.setReviewDate(LocalDate.now());
        testReview.setVerified(false);
        testReview.setAnonymous(false);
        testReview.setFlagged(false);
        testReview.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void addReview_ShouldCreate_WhenNoDuplicate() {
        AddReviewRequest req = new AddReviewRequest();
        req.setAppointmentId(5);
        req.setPatientId(2);
        req.setProviderId(1);
        req.setRating(4);
        req.setComment("Great doctor");

        when(reviewRepository.existsByAppointmentId(5)).thenReturn(false);
        when(reviewRepository.save(any())).thenReturn(testReview);
        when(reviewRepository.avgRatingByProviderId(1)).thenReturn(4.0);
        doNothing().when(restTemplate).put(anyString(), any());

        Review result = reviewService.addReview(req);

        assertNotNull(result);
        assertEquals(4, result.getRating());
        verify(reviewRepository).save(any());
    }

    @Test
    void addReview_ShouldThrow_WhenDuplicateAppointment() {
        AddReviewRequest req = new AddReviewRequest();
        req.setAppointmentId(5);
        req.setPatientId(2);
        req.setProviderId(1);
        req.setRating(4);

        when(reviewRepository.existsByAppointmentId(5)).thenReturn(true);

        assertThrows(RuntimeException.class, () -> reviewService.addReview(req));
    }

    @Test
    void addReview_ShouldThrow_WhenRatingOutOfRange() {
        AddReviewRequest req = new AddReviewRequest();
        req.setAppointmentId(6);
        req.setPatientId(2);
        req.setProviderId(1);
        req.setRating(6); // invalid

        when(reviewRepository.existsByAppointmentId(6)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> reviewService.addReview(req));
    }

    @Test
    void updateReview_ShouldUpdateRatingAndComment() {
        UpdateReviewRequest req = new UpdateReviewRequest();
        req.setRating(5);
        req.setComment("Updated: excellent!");

        when(reviewRepository.findById(1)).thenReturn(Optional.of(testReview));
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reviewRepository.avgRatingByProviderId(1)).thenReturn(4.5);
        doNothing().when(restTemplate).put(anyString(), any());

        Review updated = reviewService.updateReview(1, req);

        assertEquals(5, updated.getRating());
        assertEquals("Updated: excellent!", updated.getComment());
        assertFalse(updated.isVerified()); // reset on edit
    }

    @Test
    void flagReview_ShouldSetFlaggedTrue() {
        when(reviewRepository.findById(1)).thenReturn(Optional.of(testReview));
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        reviewService.flagReview(1, "Contains false info");

        assertTrue(testReview.isFlagged());
        assertEquals("Contains false info", testReview.getFlagReason());
    }

    @Test
    void unflagReview_ShouldClearFlag() {
        testReview.setFlagged(true);
        testReview.setFlagReason("Some reason");

        when(reviewRepository.findById(1)).thenReturn(Optional.of(testReview));
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        reviewService.unflagReview(1);

        assertFalse(testReview.isFlagged());
        assertNull(testReview.getFlagReason());
    }

    @Test
    void verifyReview_ShouldSetVerifiedAndClearFlag() {
        testReview.setFlagged(true);

        when(reviewRepository.findById(1)).thenReturn(Optional.of(testReview));
        when(reviewRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        reviewService.verifyReview(1);

        assertTrue(testReview.isVerified());
        assertFalse(testReview.isFlagged());
    }

    @Test
    void getRatingSummary_ShouldReturnCorrectData() {
        when(reviewRepository.avgRatingByProviderId(1)).thenReturn(4.3);
        when(reviewRepository.countByProviderId(1)).thenReturn(10);
        when(reviewRepository.getRatingDistribution(1)).thenReturn(
            List.of(
                new Object[]{5, 5L},
                new Object[]{4, 3L},
                new Object[]{3, 2L}
            )
        );

        RatingSummary summary = reviewService.getRatingSummary(1);

        assertEquals(1, summary.getProviderId());
        assertEquals(4.3, summary.getAvgRating());
        assertEquals(10, summary.getTotalReviews());
        assertEquals(5L, summary.getDistribution().get(5));
        assertEquals(3L, summary.getDistribution().get(4));
        assertEquals(0L, summary.getDistribution().get(1)); // initialised to 0
    }

    @Test
    void getReviewById_ShouldThrow_WhenNotFound() {
        when(reviewRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> reviewService.getReviewById(99));
    }
}
