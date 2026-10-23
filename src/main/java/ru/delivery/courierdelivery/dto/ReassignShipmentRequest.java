package ru.delivery.courierdelivery.dto;

import jakarta.validation.constraints.NotNull;

public record ReassignShipmentRequest(
        @NotNull(message = "Отправление обязательно") Long shipmentId,
        @NotNull(message = "Новая остановка обязательна") Long newStopId
) {
}
