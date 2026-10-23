package ru.delivery.courierdelivery.domain;

/**
 * Статусы предметной области. Бизнес-операции опираются на допустимые переходы.
 */
public final class Statuses {

    private Statuses() {
    }

    public static final String SHIPMENT_CREATED = "CREATED";
    public static final String SHIPMENT_ASSIGNED = "ASSIGNED";
    public static final String SHIPMENT_IN_TRANSIT = "IN_TRANSIT";
    public static final String SHIPMENT_DELIVERED = "DELIVERED";
    public static final String SHIPMENT_CANCELLED = "CANCELLED";

    public static final String ROUTE_PLANNED = "PLANNED";
    public static final String ROUTE_IN_PROGRESS = "IN_PROGRESS";
    public static final String ROUTE_COMPLETED = "COMPLETED";
    public static final String ROUTE_CANCELLED = "CANCELLED";

    public static final String ASSIGNMENT_PENDING = "PENDING";
    public static final String ASSIGNMENT_IN_PROGRESS = "IN_PROGRESS";
    public static final String ASSIGNMENT_COMPLETED = "COMPLETED";
    public static final String ASSIGNMENT_FAILED = "FAILED";
}
