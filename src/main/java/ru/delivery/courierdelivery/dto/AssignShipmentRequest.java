package ru.delivery.courierdelivery.dto;

import jakarta.validation.constraints.NotNull;

public record AssignShipmentRequest(
        @NotNull(message = "Отправление обязательно") Long shipmentId,
        @NotNull(message = "Остановка обязательна") Long stopId
) {
}
