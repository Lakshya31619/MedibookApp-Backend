package com.medibook.provider.resource;

import com.medibook.provider.dto.ProviderDto.*;
import com.medibook.provider.entity.Provider;
import com.medibook.provider.service.ProviderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/providers")
@CrossOrigin(origins = "*")
public class ProviderResource {

    @Autowired
    private ProviderService providerService;

    @GetMapping
    public ResponseEntity<List<ProviderSummary>> getAllVerified() {
        List<ProviderSummary> summaries = providerService.getVerifiedProviders()
                .stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable int id) {
        try {
            return ResponseEntity.ok(toResponse(providerService.getProviderById(id)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProviderSummary>> search(@RequestParam String q) {
        List<ProviderSummary> results = providerService.searchProviders(q)
                .stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/specialization/{spec}")
    public ResponseEntity<List<ProviderSummary>> getBySpec(@PathVariable String spec) {
        return ResponseEntity.ok(
            providerService.getBySpecialization(spec)
                .stream().map(this::toSummary).toList()
        );
    }

    @GetMapping("/location")
    public ResponseEntity<List<ProviderSummary>> getByLocation(@RequestParam String city) {
        return ResponseEntity.ok(
            providerService.getByLocation(city)
                .stream().map(this::toSummary).toList()
        );
    }

    @GetMapping("/rating")
    public ResponseEntity<List<ProviderSummary>> getByRating(
            @RequestParam double min) {
        return ResponseEntity.ok(
            providerService.getByMinRating(min)
                .stream().map(this::toSummary).toList()
        );
    }

    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterProviderRequest request) {
        try {
            Provider provider = providerService.registerProvider(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Provider profile created. Awaiting admin verification.",
                "providerId", provider.getProviderId(),
                "isVerified", provider.isVerified()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/my/{userId}")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> getMyProfile(@PathVariable int userId) {
        try {
            return ResponseEntity.ok(
                toResponse(providerService.getProviderByUserId(userId)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> update(@PathVariable int id,
                                    @RequestBody UpdateProviderRequest request) {
        try {
            return ResponseEntity.ok(
                toResponse(providerService.updateProvider(id, request)));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('PROVIDER','ADMIN')")
    public ResponseEntity<?> setAvailability(@PathVariable int id,
                                              @RequestParam boolean status) {
        try {
            providerService.setAvailability(id, status);
            return ResponseEntity.ok(Map.of(
                "message", status ? "Provider is now available" : "Provider is now on leave",
                "isAvailable", status
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProviderResponse>> getAllForAdmin() {
        return ResponseEntity.ok(
            providerService.getAllProviders()
                .stream().map(this::toResponse).toList()
        );
    }

    @PutMapping("/{id}/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> verify(@PathVariable int id) {
        try {
            providerService.verifyProvider(id);
            return ResponseEntity.ok(Map.of(
                "message", "Provider verified successfully",
                "providerId", id
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/unverify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> unverify(@PathVariable int id) {
        try {
            providerService.unverifyProvider(id);
            return ResponseEntity.ok(Map.of("message", "Provider verification removed"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}/rating")
    public ResponseEntity<?> updateRating(@PathVariable int id,
                                           @RequestParam double value) {
        try {
            providerService.updateRating(id, value);
            return ResponseEntity.ok(Map.of("message", "Rating updated", "newRating", value));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> delete(@PathVariable int id) {
        try {
            providerService.deleteProvider(id);
            return ResponseEntity.ok(Map.of("message", "Provider deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/analytics/specializations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SpecializationCount>> getSpecializationStats() {
        return ResponseEntity.ok(providerService.getSpecializationCounts());
    }

    private ProviderResponse toResponse(Provider p) {
        ProviderResponse r = new ProviderResponse();
        r.setProviderId(p.getProviderId());
        r.setUserId(p.getUserId());
        r.setSpecialization(p.getSpecialization());
        r.setQualification(p.getQualification());
        r.setExperienceYears(p.getExperienceYears());
        r.setBio(p.getBio());
        r.setClinicName(p.getClinicName());
        r.setClinicAddress(p.getClinicAddress());
        r.setAvgRating(p.getAvgRating());
        r.setAvailable(p.isAvailable());
        r.setVerified(p.isVerified());
        r.setConsultationFee(p.getConsultationFee());
        r.setProfilePicUrl(p.getProfilePicUrl());
        r.setCreatedAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
        return r;
    }

    private ProviderSummary toSummary(Provider p) {
        ProviderSummary s = new ProviderSummary();
        s.setProviderId(p.getProviderId());
        s.setSpecialization(p.getSpecialization());
        s.setClinicName(p.getClinicName());
        s.setClinicAddress(p.getClinicAddress());
        s.setAvgRating(p.getAvgRating());
        s.setAvailable(p.isAvailable());
        s.setConsultationFee(p.getConsultationFee());
        s.setProfilePicUrl(p.getProfilePicUrl());
        s.setExperienceYears(p.getExperienceYears());
        return s;
    }
}