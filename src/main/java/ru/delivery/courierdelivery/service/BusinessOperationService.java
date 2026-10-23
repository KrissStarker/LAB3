package ru.delivery.courierdelivery.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.delivery.courierdelivery.domain.Statuses;
import ru.delivery.courierdelivery.model.Route;
import ru.delivery.courierdelivery.model.Shipment;
import ru.delivery.courierdelivery.model.ShipmentAssignment;
import ru.delivery.courierdelivery.model.Stop;
import ru.delivery.courierdelivery.repository.RouteRepository;
import ru.delivery.courierdelivery.repository.ShipmentAssignmentRepository;
import ru.delivery.courierdelivery.repository.ShipmentRepository;
import ru.delivery.courierdelivery.repository.StopRepository;

@Service
@Transactional
public class BusinessOperationService {

    private final ShipmentRepository shipmentRepository;
    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final ShipmentAssignmentRepository assignmentRepository;
    private final RouteService routeService;

    public BusinessOperationService(
            ShipmentRepository shipmentRepository,
            StopRepository stopRepository,
            RouteRepository routeRepository,
            ShipmentAssignmentRepository assignmentRepository,
            RouteService routeService
    ) {
        this.shipmentRepository = shipmentRepository;
        this.stopRepository = stopRepository;
        this.routeRepository = routeRepository;
        this.assignmentRepository = assignmentRepository;
        this.routeService = routeService;
    }

    public Map<String, Object> assignShipment(Long shipmentId, Long stopId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Отправление не найдено"));
        Stop stop = stopRepository.findById(stopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Остановка не найдена"));
        Route route = stop.getRoute();

        if (!Statuses.SHIPMENT_CREATED.equals(shipment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Назначить можно только CREATED, сейчас: " + shipment.getStatus());
        }
        if (Statuses.ROUTE_COMPLETED.equals(route.getStatus()) || Statuses.ROUTE_CANCELLED.equals(route.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Маршрут недоступен: " + route.getStatus());
        }
        if (!assignmentRepository.findByShipment_Id(shipmentId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Отправление уже назначено");
        }

        routeService.assertFitsLocked(route.getId(), shipment.getWeight(), null);

        String assignmentStatus;
        String shipmentStatus;
        if (Statuses.ROUTE_IN_PROGRESS.equals(route.getStatus())) {
            assignmentStatus = Statuses.ASSIGNMENT_IN_PROGRESS;
            shipmentStatus = Statuses.SHIPMENT_IN_TRANSIT;
        } else if (Statuses.ROUTE_PLANNED.equals(route.getStatus())) {
            assignmentStatus = Statuses.ASSIGNMENT_PENDING;
            shipmentStatus = Statuses.SHIPMENT_ASSIGNED;
        } else {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нужен маршрут PLANNED или IN_PROGRESS");
        }

        ShipmentAssignment assignment = new ShipmentAssignment();
        assignment.setShipment(shipment);
        assignment.setStop(stop);
        assignment.setStatus(assignmentStatus);
        assignment = assignmentRepository.save(assignment);

        shipment.setStatus(shipmentStatus);
        shipmentRepository.save(shipment);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assignmentId", assignment.getId());
        result.put("shipmentId", shipment.getId());
        result.put("shipmentStatus", shipment.getStatus());
        result.put("assignmentStatus", assignment.getStatus());
        result.put("stopId", stop.getId());
        result.put("routeId", route.getId());
        result.put("routeCurrentWeight", routeService.currentWeight(route.getId()));
        return result;
    }

    public Map<String, Object> startRoute(Long routeId) {
        Route route = routeRepository.findByIdForUpdate(routeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Маршрут не найден"));
        if (!Statuses.ROUTE_PLANNED.equals(route.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нужен PLANNED, сейчас: " + route.getStatus());
        }
        if (stopRepository.findByRoute_Id(routeId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "У маршрута нет остановок");
        }
        List<ShipmentAssignment> assignments = assignmentRepository.findByStop_Route_Id(routeId);
        if (assignments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нет назначенных отправлений");
        }
        route.setStatus(Statuses.ROUTE_IN_PROGRESS);
        routeRepository.save(route);
        int a = 0, s = 0;
        for (ShipmentAssignment assignment : assignments) {
            if (Statuses.ASSIGNMENT_PENDING.equals(assignment.getStatus())) {
                assignment.setStatus(Statuses.ASSIGNMENT_IN_PROGRESS);
                assignmentRepository.save(assignment);
                a++;
            }
            Shipment shipment = assignment.getShipment();
            if (Statuses.SHIPMENT_ASSIGNED.equals(shipment.getStatus())) {
                shipment.setStatus(Statuses.SHIPMENT_IN_TRANSIT);
                shipmentRepository.save(shipment);
                s++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("routeId", route.getId());
        result.put("routeStatus", route.getStatus());
        result.put("assignmentsStarted", a);
        result.put("shipmentsInTransit", s);
        return result;
    }

    public Map<String, Object> completeDelivery(Long assignmentId) {
        ShipmentAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Назначение не найдено"));
        Route route = assignment.getStop().getRoute();
        Shipment shipment = assignment.getShipment();
        if (Statuses.ASSIGNMENT_COMPLETED.equals(assignment.getStatus())
                || Statuses.ASSIGNMENT_FAILED.equals(assignment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Уже финальный статус: " + assignment.getStatus());
        }
        if (!Statuses.ROUTE_IN_PROGRESS.equals(route.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Маршрут должен быть IN_PROGRESS");
        }
        if (Statuses.SHIPMENT_CANCELLED.equals(shipment.getStatus())
                || Statuses.SHIPMENT_DELIVERED.equals(shipment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Отправление: " + shipment.getStatus());
        }
        assignment.setStatus(Statuses.ASSIGNMENT_COMPLETED);
        assignmentRepository.save(assignment);
        shipment.setStatus(Statuses.SHIPMENT_DELIVERED);
        shipmentRepository.save(shipment);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assignmentId", assignment.getId());
        result.put("assignmentStatus", assignment.getStatus());
        result.put("shipmentId", shipment.getId());
        result.put("shipmentStatus", shipment.getStatus());
        result.put("routeId", route.getId());
        return result;
    }

    public Map<String, Object> cancelShipment(Long shipmentId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Отправление не найдено"));
        if (Statuses.SHIPMENT_DELIVERED.equals(shipment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Доставленное нельзя отменить");
        }
        if (Statuses.SHIPMENT_CANCELLED.equals(shipment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Уже отменено");
        }
        Long removed = null, routeId = null;
        for (ShipmentAssignment link : assignmentRepository.findByShipment_Id(shipmentId)) {
            if (Statuses.ASSIGNMENT_COMPLETED.equals(link.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Назначение уже выполнено");
            }
            removed = link.getId();
            routeId = link.getStop().getRoute().getId();
            assignmentRepository.delete(link);
        }
        assignmentRepository.flush();
        shipment.setStatus(Statuses.SHIPMENT_CANCELLED);
        shipmentRepository.save(shipment);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shipmentId", shipment.getId());
        result.put("shipmentStatus", shipment.getStatus());
        result.put("removedAssignmentId", removed);
        result.put("freedRouteId", routeId);
        if (routeId != null) {
            result.put("routeCurrentWeight", routeService.currentWeight(routeId));
        }
        return result;
    }

    public Map<String, Object> reassignShipment(Long shipmentId, Long newStopId) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Отправление не найдено"));
        Stop newStop = stopRepository.findById(newStopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Остановка не найдена"));
        Route newRoute = newStop.getRoute();
        if (Statuses.SHIPMENT_DELIVERED.equals(shipment.getStatus())
                || Statuses.SHIPMENT_CANCELLED.equals(shipment.getStatus())
                || Statuses.SHIPMENT_CREATED.equals(shipment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нужен ASSIGNED/IN_TRANSIT, сейчас: " + shipment.getStatus());
        }
        if (Statuses.ROUTE_COMPLETED.equals(newRoute.getStatus())
                || Statuses.ROUTE_CANCELLED.equals(newRoute.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Целевой маршрут: " + newRoute.getStatus());
        }
        List<ShipmentAssignment> links = assignmentRepository.findByShipment_Id(shipmentId);
        if (links.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нет текущего назначения");
        }
        ShipmentAssignment current = links.getFirst();
        if (Statuses.ASSIGNMENT_COMPLETED.equals(current.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Выполненное нельзя переносить");
        }
        if (current.getStop().getId().equals(newStopId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Уже на этой остановке");
        }
        Long oldId = current.getId();
        assignmentRepository.delete(current);
        assignmentRepository.flush();
        routeService.assertFitsLocked(newRoute.getId(), shipment.getWeight(), null);
        ShipmentAssignment created = new ShipmentAssignment();
        created.setShipment(shipment);
        created.setStop(newStop);
        if (Statuses.ROUTE_IN_PROGRESS.equals(newRoute.getStatus())) {
            created.setStatus(Statuses.ASSIGNMENT_IN_PROGRESS);
            shipment.setStatus(Statuses.SHIPMENT_IN_TRANSIT);
        } else {
            created.setStatus(Statuses.ASSIGNMENT_PENDING);
            shipment.setStatus(Statuses.SHIPMENT_ASSIGNED);
        }
        created = assignmentRepository.save(created);
        shipmentRepository.save(shipment);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shipmentId", shipment.getId());
        result.put("shipmentStatus", shipment.getStatus());
        result.put("assignmentStatus", created.getStatus());
        result.put("oldAssignmentId", oldId);
        result.put("newAssignmentId", created.getId());
        result.put("newStopId", newStop.getId());
        result.put("newRouteId", newRoute.getId());
        result.put("routeCurrentWeight", routeService.currentWeight(newRoute.getId()));
        return result;
    }

    public Map<String, Object> closeRoute(Long routeId) {
        Route route = routeRepository.findByIdForUpdate(routeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Маршрут не найден"));
        if (!Statuses.ROUTE_IN_PROGRESS.equals(route.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нужен IN_PROGRESS, сейчас: " + route.getStatus());
        }
        List<ShipmentAssignment> assignments = assignmentRepository.findByStop_Route_Id(routeId);
        if (assignments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нет назначений");
        }
        long notDone = assignments.stream()
                .filter(a -> !Statuses.ASSIGNMENT_COMPLETED.equals(a.getStatus())
                        && !Statuses.ASSIGNMENT_FAILED.equals(a.getStatus()))
                .count();
        if (notDone > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Осталось незавершённых: " + notDone);
        }
        route.setStatus(Statuses.ROUTE_COMPLETED);
        routeRepository.save(route);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("routeId", route.getId());
        result.put("routeStatus", route.getStatus());
        result.put("completedAssignments", assignments.size());
        return result;
    }
}
