package ru.delivery.courierdelivery.dto;

public record BusinessOperationResponse(
        String operation,
        String message,
        Object result
) {
}
