package com.medibook.provider.serviceimpl;

import com.medibook.provider.dto.ProviderDto.*;
import com.medibook.provider.entity.Provider;
import com.medibook.provider.repository.ProviderRepository;
import com.medibook.provider.service.ProviderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProviderServiceImpl implements ProviderService {

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    @Value("${notification.service.url:http://localhost:8087}")
    private String notificationServiceUrl;

    @Override
    @Transactional
    public Provider registerProvider(RegisterProviderRequest request) {

        if (providerRepository.existsByUserId(request.getUserId())) {
            throw new RuntimeException(
                "A provider profile already exists for userId: " + request.getUserId());
        }

        Provider provider = new Provider();
        provider.setUserId(request.getUserId());
        provider.setProviderName(request.getProviderName());
        provider.setSpecialization(request.getSpecialization());
        provider.setQualification(request.getQualification());
        provider.setExperienceYears(request.getExperienceYears());
        provider.setBio(request.getBio());
        provider.setClinicName(request.getClinicName());
        provider.setClinicAddress(request.getClinicAddress());
        provider.setConsultationFee(request.getConsultationFee());
        provider.setProfilePicUrl(request.getProfilePicUrl());

        provider.setVerified(false);
        provider.setVerificationStatus("PENDING");
        provider.setAvailable(true);
        provider.setAvgRating(0.0);

        return providerRepository.save(provider);
    }

    @Override
    public Provider getProviderById(int providerId) {
        return providerRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException(
                    "Provider not found with id: " + providerId));
    }

    @Override
    public Provider getProviderByUserId(int userId) {
        return providerRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException(
                    "No provider profile found for userId: " + userId));
    }

    @Override
    public List<Provider> getBySpecialization(String specialization) {
        return providerRepository
                .findBySpecializationAndIsVerifiedAndIsAvailable(
                    specialization, true, true);
    }

    @Override
    public List<Provider> searchProviders(String keyword) {
        return providerRepository.searchProviders(keyword)
                .stream()
                .filter(Provider::isVerified)
                .collect(Collectors.toList());
    }

    @Override
    public List<Provider> getAllProviders() {
        return providerRepository.findAll();
    }

    @Override
    public List<Provider> getVerifiedProviders() {
        return providerRepository.findByIsVerifiedOrderByAvgRatingDesc(true);
    }

    @Override
    public List<Provider> getPendingProviders() {
        return providerRepository.findByVerificationStatus("PENDING");
    }

    @Override
    public List<Provider> getByMinRating(double minRating) {
        return providerRepository
                .findByAvgRatingGreaterThanEqualAndIsVerified(minRating, true);
    }

    @Override
    public List<Provider> getByLocation(String location) {
        return providerRepository
                .findByClinicAddressContainingIgnoreCaseAndIsVerified(location, true);
    }

    @Override
    public List<SpecializationCount> getSpecializationCounts() {
        return providerRepository.countBySpecialization()
                .stream()
                .map(row -> new SpecializationCount(
                    (String) row[0],
                    (Long)   row[1]
                ))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isProviderVerified(int providerId) {
        return getProviderById(providerId).isVerified();
    }

    @Override
    @Transactional
    public Provider updateProvider(int providerId, UpdateProviderRequest request) {
        Provider provider = getProviderById(providerId);

        if ("REJECTED".equals(provider.getVerificationStatus())) {
            throw new RuntimeException(
                "Your profile has been rejected. Reason: " + provider.getRejectionReason() +
                ". Please contact admin.");
        }

        if (request.getSpecialization() != null && !request.getSpecialization().isBlank())
            provider.setSpecialization(request.getSpecialization());
        if (request.getQualification() != null && !request.getQualification().isBlank())
            provider.setQualification(request.getQualification());
        if (request.getExperienceYears() > 0)
            provider.setExperienceYears(request.getExperienceYears());
        if (request.getBio() != null)
            provider.setBio(request.getBio());
        if (request.getClinicName() != null)
            provider.setClinicName(request.getClinicName());
        if (request.getClinicAddress() != null)
            provider.setClinicAddress(request.getClinicAddress());
        if (request.getConsultationFee() > 0)
            provider.setConsultationFee(request.getConsultationFee());
        if (request.getProfilePicUrl() != null)
            provider.setProfilePicUrl(request.getProfilePicUrl());

        return providerRepository.save(provider);
    }

    @Override
    @Transactional
    public void verifyProvider(int providerId) {
        Provider provider = getProviderById(providerId);
        provider.setVerified(true);
        provider.setVerificationStatus("APPROVED");
        provider.setRejectionReason(null);
        providerRepository.save(provider);

        sendProviderEvent(
            "PROVIDER_APPROVED",
            provider.getUserId(),
            provider.getProviderName(),
            null
        );
    }

    @Override
    @Transactional
    public void rejectProvider(int providerId, String reason) {
        Provider provider = getProviderById(providerId);

        if ("APPROVED".equals(provider.getVerificationStatus())) {
            throw new RuntimeException(
                "Provider is already approved. Use unverify first if needed.");
        }

        provider.setVerified(false);
        provider.setVerificationStatus("REJECTED");
        provider.setRejectionReason(reason);
        providerRepository.save(provider);

        sendProviderEvent(
            "PROVIDER_REJECTED",
            provider.getUserId(),
            provider.getProviderName(),
            reason
        );
    }

    @Override
    @Transactional
    public void unverifyProvider(int providerId) {
        Provider provider = getProviderById(providerId);
        provider.setVerified(false);
        provider.setVerificationStatus("PENDING");
        provider.setRejectionReason(null);
        providerRepository.save(provider);
    }

    @Override
    @Transactional
    public void deleteProvider(int providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new RuntimeException("Provider not found with id: " + providerId);
        }
        providerRepository.deleteById(providerId);
    }

    @Override
    @Transactional
    public void setAvailability(int providerId, boolean isAvailable) {
        Provider provider = getProviderById(providerId);

        if (!provider.isVerified()) {
            throw new RuntimeException(
                "Only verified providers can change availability. " +
                "Current status: " + provider.getVerificationStatus());
        }

        provider.setAvailable(isAvailable);
        providerRepository.save(provider);
    }

    @Override
    @Transactional
    public void updateRating(int providerId, double newRating) {
        Provider provider = getProviderById(providerId);
        double clamped = Math.max(0.0, Math.min(5.0, newRating));
        provider.setAvgRating(clamped);
        providerRepository.save(provider);
    }

    private void sendProviderEvent(String eventType, int recipientUserId,
                                    String providerName, String rejectionReason) {
        if (restTemplate == null) return;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType",    eventType);
            payload.put("providerId",   recipientUserId);
            payload.put("providerName", providerName != null ? providerName : "Provider");
            if (rejectionReason != null) payload.put("rejectionReason", rejectionReason);

            restTemplate.postForObject(
                notificationServiceUrl + "/notifications/events/provider",
                payload,
                Map.class
            );
        } catch (Exception e) {
            System.err.println("Warning: Could not send provider notification event: "
                + e.getMessage());
        }
    }
}