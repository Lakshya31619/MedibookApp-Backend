package com.medibook.provider.repository;

import com.medibook.provider.entity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Integer> {

    Optional<Provider> findByUserId(int userId);

    List<Provider> findBySpecialization(String specialization);

    List<Provider> findByVerified(boolean verified);

    List<Provider> findByAvailable(boolean available);

    List<Provider> findBySpecializationAndVerifiedAndAvailable(
            String specialization, boolean verified, boolean available);

    @Query("SELECT p FROM Provider p WHERE " +
           "LOWER(p.providerName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.specialization) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.clinicAddress) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.clinicName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Provider> searchProviders(@Param("keyword") String keyword);

    List<Provider> findByClinicAddressContainingIgnoreCaseAndVerified(
            String location, boolean verified);

    @Query("SELECT p.specialization, COUNT(p) FROM Provider p GROUP BY p.specialization")
    List<Object[]> countBySpecialization();

    List<Provider> findByVerifiedOrderByAvgRatingDesc(boolean verified);

    boolean existsByUserId(int userId);

    List<Provider> findByAvgRatingGreaterThanEqualAndVerified(
            double minRating, boolean verified);

    List<Provider> findByVerificationStatus(String verificationStatus);
}