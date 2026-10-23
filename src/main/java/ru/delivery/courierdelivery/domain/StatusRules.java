package ru.delivery.courierdelivery.domain;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class StatusRules {

    private StatusRules() {
    }

    public static final Set<String> SHIPMENT = Set.of(
            Statuses.SHIPMENT_CREATED,
            Statuses.SHIPMENT_ASSIGNED,
            Statuses.SHIPMENT_IN_TRANSIT,
            Statuses.SHIPMENT_DELIVERED,
            Statuses.SHIPMENT_CANCELLED
    );

    public static final Set<String> ROUTE = Set.of(
            Statuses.ROUTE_PLANNED,
            Statuses.ROUTE_IN_PROGRESS,
            Statuses.ROUTE_COMPLETED,
            Statuses.ROUTE_CANCELLED
    );

    public static final Set<String> ASSIGNMENT = Set.of(
            Statuses.ASSIGNMENT_PENDING,
            Statuses.ASSIGNMENT_IN_PROGRESS,
            Statuses.ASSIGNMENT_COMPLETED,
            Statuses.ASSIGNMENT_FAILED
    );

    public static String requireShipment(String status) {
        String value = normalize(status);
        if (!SHIPMENT.contains(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Недопустимый статус отправления: " + status + ". Допустимы: " + SHIPMENT
            );
        }
        return value;
    }

    public static String requireRoute(String status) {
        String value = normalize(status);
        if (!ROUTE.contains(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Недопустимый статус маршрута: " + status + ". Допустимы: " + ROUTE
            );
        }
        return value;
    }

    public static String requireAssignment(String status) {
        String value = normalize(status);
        if (!ASSIGNMENT.contains(value)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Недопустимый статус назначения: " + status + ". Допустимы: " + ASSIGNMENT
            );
        }
        return value;
    }

    public static boolean isActiveAssignment(String status) {
        return Statuses.ASSIGNMENT_PENDING.equals(status)
                || Statuses.ASSIGNMENT_IN_PROGRESS.equals(status);
    }

    private static String normalize(String status) {
        if (status == null || status.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Статус не может быть пустым");
        }
        return status.trim().toUpperCase();
    }
}
