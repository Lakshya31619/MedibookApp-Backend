package com.medibook.provider;

import com.medibook.provider.dto.ProviderDto.*;
import com.medibook.provider.entity.Provider;
import com.medibook.provider.repository.ProviderRepository;
import com.medibook.provider.serviceimpl.ProviderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProviderServiceImplTest {

    @Mock
    private ProviderRepository providerRepository;

    @InjectMocks
    private ProviderServiceImpl providerService;

    private Provider testProvider;

    @BeforeEach
    void setUp() {
        testProvider = new Provider();
        testProvider.setProviderId(1);
        testProvider.setUserId(5);
        testProvider.setSpecialization("Cardiology");
        testProvider.setQualification("MBBS, MD");
        testProvider.setExperienceYears(10);
        testProvider.setClinicName("Heart Care");
        testProvider.setClinicAddress("Mumbai");
        testProvider.setAvgRating(0.0);
        testProvider.setVerified(false);
        testProvider.setAvailable(true);
    }

    @Test
    void register_ShouldCreateProvider_WhenUserIdIsNew() {
        RegisterProviderRequest req = new RegisterProviderRequest();
        req.setUserId(5);
        req.setSpecialization("Cardiology");
        req.setQualification("MBBS, MD");
        req.setExperienceYears(10);

        when(providerRepository.existsByUserId(5)).thenReturn(false);
        when(providerRepository.save(any(Provider.class))).thenReturn(testProvider);

        Provider result = providerService.registerProvider(req);

        assertNotNull(result);
        assertEquals("Cardiology", result.getSpecialization());
        assertFalse(result.isVerified()); // must start unverified
        verify(providerRepository).save(any(Provider.class));
    }

    @Test
    void register_ShouldThrow_WhenUserAlreadyHasProfile() {
        RegisterProviderRequest req = new RegisterProviderRequest();
        req.setUserId(5);
        req.setSpecialization("Cardiology");
        req.setQualification("MBBS");

        when(providerRepository.existsByUserId(5)).thenReturn(true);

        assertThrows(RuntimeException.class,
            () -> providerService.registerProvider(req));
    }

    @Test
    void verifyProvider_ShouldSetVerifiedTrue() {
        when(providerRepository.findById(1)).thenReturn(Optional.of(testProvider));
        when(providerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        providerService.verifyProvider(1);

        assertTrue(testProvider.isVerified());
        verify(providerRepository).save(testProvider);
    }

    @Test
    void setAvailability_ShouldToggleCorrectly() {
        when(providerRepository.findById(1)).thenReturn(Optional.of(testProvider));
        when(providerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        providerService.setAvailability(1, false);

        assertFalse(testProvider.isAvailable());
    }

    @Test
    void updateRating_ShouldClampToValidRange() {
        when(providerRepository.findById(1)).thenReturn(Optional.of(testProvider));
        when(providerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        providerService.updateRating(1, 6.5); // above max

        assertEquals(5.0, testProvider.getAvgRating());
    }

    @Test
    void getProviderById_ShouldThrow_WhenNotFound() {
        when(providerRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
            () -> providerService.getProviderById(99));
    }

    @Test
    void searchProviders_ShouldReturnOnlyVerified() {
        Provider unverified = new Provider();
        unverified.setVerified(false);
        unverified.setSpecialization("Cardiology");

        Provider verified = new Provider();
        verified.setVerified(true);
        verified.setSpecialization("Cardiology");

        when(providerRepository.searchProviders("cardio"))
            .thenReturn(List.of(unverified, verified));

        List<Provider> results = providerService.searchProviders("cardio");

        assertEquals(1, results.size());
        assertTrue(results.get(0).isVerified());
    }
}
