package ru.delivery.courierdelivery.controller;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.delivery.courierdelivery.dto.AssignShipmentRequest;
import ru.delivery.courierdelivery.dto.BusinessOperationResponse;
import ru.delivery.courierdelivery.dto.ReassignShipmentRequest;
import ru.delivery.courierdelivery.service.BusinessOperationService;

@RestController
@RequestMapping("/api/operations")
public class BusinessOperationController {

    private final BusinessOperationService businessOperationService;

    public BusinessOperationController(BusinessOperationService businessOperationService) {
        this.businessOperationService = businessOperationService;
    }

    @PostMapping("/assign-shipment")
    public ResponseEntity<BusinessOperationResponse> assignShipment(
            @Valid @RequestBody AssignShipmentRequest request
    ) {
        Map<String, Object> result = businessOperationService.assignShipment(
                request.shipmentId(),
                request.stopId()
        );
        return ResponseEntity.ok(new BusinessOperationResponse(
                "assign-shipment",
                "Отправление назначено на остановку",
                result
        ));
    }

    @PostMapping("/start-route/{routeId}")
    public ResponseEntity<BusinessOperationResponse> startRoute(@PathVariable Long routeId) {
        Map<String, Object> result = businessOperationService.startRoute(routeId);
        return ResponseEntity.ok(new BusinessOperationResponse(
                "start-route",
                "Маршрут запущен",
                result
        ));
    }

    @PostMapping("/complete-delivery/{assignmentId}")
    public ResponseEntity<BusinessOperationResponse> completeDelivery(@PathVariable Long assignmentId) {
        Map<String, Object> result = businessOperationService.completeDelivery(assignmentId);
        return ResponseEntity.ok(new BusinessOperationResponse(
                "complete-delivery",
                "Доставка завершена",
                result
        ));
    }

    @PostMapping("/cancel-shipment/{shipmentId}")
    public ResponseEntity<BusinessOperationResponse> cancelShipment(@PathVariable Long shipmentId) {
        Map<String, Object> result = businessOperationService.cancelShipment(shipmentId);
        return ResponseEntity.ok(new BusinessOperationResponse(
                "cancel-shipment",
                "Отправление отменено",
                result
        ));
    }

    @PostMapping("/reassign-shipment")
    public ResponseEntity<BusinessOperationResponse> reassignShipment(
            @Valid @RequestBody ReassignShipmentRequest request
    ) {
        Map<String, Object> result = businessOperationService.reassignShipment(
                request.shipmentId(),
                request.newStopId()
        );
        return ResponseEntity.ok(new BusinessOperationResponse(
                "reassign-shipment",
                "Отправление переназначено",
                result
        ));
    }

    @PostMapping("/close-route/{routeId}")
    public ResponseEntity<BusinessOperationResponse> closeRoute(@PathVariable Long routeId) {
        Map<String, Object> result = businessOperationService.closeRoute(routeId);
        return ResponseEntity.ok(new BusinessOperationResponse(
                "close-route",
                "Маршрут закрыт",
                result
        ));
    }
}
