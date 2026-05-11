package com.medibook.notification.entity;

public final class NotificationType {

    private NotificationType() {}

    public static final String APPOINTMENT_BOOKED    = "APPOINTMENT_BOOKED";
    public static final String APPOINTMENT_CONFIRMED = "APPOINTMENT_CONFIRMED";
    public static final String APPOINTMENT_CANCELLED = "APPOINTMENT_CANCELLED";
    public static final String APPOINTMENT_RESCHEDULED = "APPOINTMENT_RESCHEDULED";
    public static final String APPOINTMENT_REMINDER  = "APPOINTMENT_REMINDER";
    public static final String APPOINTMENT_COMPLETED = "APPOINTMENT_COMPLETED";
    public static final String APPOINTMENT_NO_SHOW   = "APPOINTMENT_NO_SHOW";

    public static final String NEW_BOOKING_FOR_PROVIDER = "NEW_BOOKING_FOR_PROVIDER";
    public static final String BOOKING_CANCELLED_FOR_PROVIDER = "BOOKING_CANCELLED_FOR_PROVIDER";
    public static final String PROVIDER_REGISTERED   = "PROVIDER_REGISTERED";
    public static final String PROVIDER_APPROVED     = "PROVIDER_APPROVED";
    public static final String PROVIDER_REJECTED     = "PROVIDER_REJECTED";

    public static final String PAYMENT_RECEIVED      = "PAYMENT_RECEIVED";
    public static final String PAYMENT_REFUNDED      = "PAYMENT_REFUNDED";
    public static final String ADMIN_PAYMENT_RECEIVED = "ADMIN_PAYMENT_RECEIVED";
    public static final String ADMIN_PAYMENT_REFUNDED = "ADMIN_PAYMENT_REFUNDED";

    public static final String REVIEW_RECEIVED       = "REVIEW_RECEIVED";

    public static final String GENERAL               = "GENERAL";
}