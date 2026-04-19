package com.medibook.provider.service;

import com.medibook.provider.dto.ProviderDto.*;
import com.medibook.provider.entity.Provider;

import java.util.List;

public interface ProviderService {

    Provider registerProvider(RegisterProviderRequest request);

    Provider getProviderById(int providerId);

    Provider getProviderByUserId(int userId);

    List<Provider> getBySpecialization(String specialization);

    List<Provider> searchProviders(String keyword);

    List<Provider> getAllProviders();

    List<Provider> getVerifiedProviders();

    Provider updateProvider(int providerId, UpdateProviderRequest request);

    void verifyProvider(int providerId);

    void unverifyProvider(int providerId);

    void setAvailability(int providerId, boolean isAvailable);

    void deleteProvider(int providerId);

    void updateRating(int providerId, double newRating);

    List<Provider> getByMinRating(double minRating);

    List<SpecializationCount> getSpecializationCounts();

    List<Provider> getByLocation(String location);
}