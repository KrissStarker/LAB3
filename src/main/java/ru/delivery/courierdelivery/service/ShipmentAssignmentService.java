package ru.delivery.courierdelivery.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.delivery.courierdelivery.domain.StatusRules;
import ru.delivery.courierdelivery.domain.Statuses;
import ru.delivery.courierdelivery.model.Route;
import ru.delivery.courierdelivery.model.Shipment;
import ru.delivery.courierdelivery.model.ShipmentAssignment;
import ru.delivery.courierdelivery.model.Stop;
import ru.delivery.courierdelivery.repository.ShipmentAssignmentRepository;
import ru.delivery.courierdelivery.repository.ShipmentRepository;
import ru.delivery.courierdelivery.repository.StopRepository;

@Service
@Transactional
public class ShipmentAssignmentService {

    private final ShipmentAssignmentRepository assignmentRepository;
    private final ShipmentRepository shipmentRepository;
    private final StopRepository stopRepository;
    private final RouteService routeService;

    public ShipmentAssignmentService(
            ShipmentAssignmentRepository assignmentRepository,
            ShipmentRepository shipmentRepository,
            StopRepository stopRepository,
            RouteService routeService
    ) {
        this.assignmentRepository = assignmentRepository;
        this.shipmentRepository = shipmentRepository;
        this.stopRepository = stopRepository;
        this.routeService = routeService;
    }

    @Transactional(readOnly = true)
    public List<ShipmentAssignment> findAll() {
        return assignmentRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public ShipmentAssignment findById(Long id) {
        return assignmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Назначение не найдено"));
    }

    public ShipmentAssignment create(Long shipmentId, Long stopId, String status) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Отправление не найдено"));
        Stop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Остановка не найдена"));
        Route route = stop.getRoute();

        if (!Statuses.SHIPMENT_CREATED.equals(shipment.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Назначить можно только отправление CREATED, сейчас: " + shipment.getStatus()
            );
        }
        if (!assignmentRepository.findByShipment_Id(shipmentId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "У отправления уже есть назначение");
        }
        if (Statuses.ROUTE_COMPLETED.equals(route.getStatus())
                || Statuses.ROUTE_CANCELLED.equals(route.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Нельзя назначать на маршрут в статусе " + route.getStatus()
            );
        }

        routeService.assertFitsLocked(route.getId(), shipment.getWeight(), null);

        if (status != null && !status.isBlank()) {
            String requested = StatusRules.requireAssignment(status);
            if (Statuses.ASSIGNMENT_COMPLETED.equals(requested) || Statuses.ASSIGNMENT_FAILED.equals(requested)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Через CRUD нельзя сразу создать завершённое назначение"
                );
            }
        }

        String assignmentStatus;
        String shipmentStatus;
        if (Statuses.ROUTE_IN_PROGRESS.equals(route.getStatus())) {
            assignmentStatus = Statuses.ASSIGNMENT_IN_PROGRESS;
            shipmentStatus = Statuses.SHIPMENT_IN_TRANSIT;
        } else {
            assignmentStatus = Statuses.ASSIGNMENT_PENDING;
            shipmentStatus = Statuses.SHIPMENT_ASSIGNED;
        }

        ShipmentAssignment assignment = new ShipmentAssignment();
        assignment.setShipment(shipment);
        assignment.setStop(stop);
        assignment.setStatus(assignmentStatus);
        assignment = assignmentRepository.save(assignment);

        shipment.setStatus(shipmentStatus);
        shipmentRepository.save(shipment);
        return assignment;
    }

    public ShipmentAssignment update(Long id, Long shipmentId, Long stopId, String status) {
        ShipmentAssignment assignment = findById(id);
        String newStatus = StatusRules.requireAssignment(status);

        if (!assignment.getShipment().getId().equals(shipmentId)
                || !assignment.getStop().getId().equals(stopId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Смена отправления/остановки через CRUD запрещена. Используйте переназначение"
            );
        }
        if (Statuses.ASSIGNMENT_COMPLETED.equals(newStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Завершение доставки — только через complete-delivery"
            );
        }
        assignment.setStatus(newStatus);
        return assignmentRepository.save(assignment);
    }

    public void delete(Long id) {
        ShipmentAssignment assignment = findById(id);
        if (Statuses.ASSIGNMENT_COMPLETED.equals(assignment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя удалить завершённое назначение");
        }
        Shipment shipment = assignment.getShipment();
        assignmentRepository.delete(assignment);
        assignmentRepository.flush();
        if (Statuses.SHIPMENT_ASSIGNED.equals(shipment.getStatus())
                || Statuses.SHIPMENT_IN_TRANSIT.equals(shipment.getStatus())) {
            shipment.setStatus(Statuses.SHIPMENT_CREATED);
            shipmentRepository.save(shipment);
        }
    }
}
