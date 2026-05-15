package com.medibook.schedule.repository;

import com.medibook.schedule.entity.AvailabilitySlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SlotRepository extends JpaRepository<AvailabilitySlot, Integer> {

    List<AvailabilitySlot> findByProviderId(int providerId);

    List<AvailabilitySlot> findByProviderIdAndDate(int providerId, LocalDate date);

    @Query("SELECT s FROM AvailabilitySlot s WHERE " +
           "s.providerId = :providerId AND " +
           "s.date = :date AND " +
           "s.booked = false AND " +
           "s.blocked = false " +
           "ORDER BY s.startTime ASC")
    List<AvailabilitySlot> findAvailableByProviderAndDate(
            @Param("providerId") int providerId,
            @Param("date") LocalDate date);

    Optional<AvailabilitySlot> findBySlotId(int slotId);

    List<AvailabilitySlot> findByProviderIdAndDateBetween(
            int providerId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COUNT(s) FROM AvailabilitySlot s WHERE " +
           "s.providerId = :providerId AND " +
           "s.booked = false AND " +
           "s.blocked = false AND " +
           "s.date >= CURRENT_DATE")
    int countAvailableByProviderId(@Param("providerId") int providerId);

    void deleteBySlotId(int slotId);

    @Modifying
    @Transactional
    @Query("DELETE FROM AvailabilitySlot s WHERE " +
           "s.date < :today AND " +
           "s.booked = false")
    int deleteExpiredSlots(@Param("today") LocalDate today);

    @Query("SELECT s FROM AvailabilitySlot s WHERE " +
           "s.providerId = :providerId AND " +
           "s.booked = false AND " +
           "s.blocked = false AND " +
           "s.date >= CURRENT_DATE " +
           "ORDER BY s.date ASC, s.startTime ASC")
    List<AvailabilitySlot> findFutureAvailableByProvider(
            @Param("providerId") int providerId);

    @Query("SELECT COUNT(s) > 0 FROM AvailabilitySlot s WHERE " +
           "s.providerId = :providerId AND " +
           "s.date = :date AND " +
           "s.startTime = :startTime")
    boolean existsByProviderIdAndDateAndStartTime(
            @Param("providerId") int providerId,
            @Param("date") LocalDate date,
            @Param("startTime") java.time.LocalTime startTime);
}