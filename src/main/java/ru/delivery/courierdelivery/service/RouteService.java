package ru.delivery.courierdelivery.service;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import ru.delivery.courierdelivery.repository.RouteRepository;
import ru.delivery.courierdelivery.repository.ShipmentAssignmentRepository;
import ru.delivery.courierdelivery.repository.ShipmentRepository;
import ru.delivery.courierdelivery.repository.StopRepository;

@Service
@Transactional
public class RouteService {

    private final RouteRepository routeRepository;
    private final StopRepository stopRepository;
    private final ShipmentAssignmentRepository assignmentRepository;
    private final ShipmentRepository shipmentRepository;

    public RouteService(
            RouteRepository routeRepository,
            StopRepository stopRepository,
            ShipmentAssignmentRepository assignmentRepository,
            ShipmentRepository shipmentRepository
    ) {
        this.routeRepository = routeRepository;
        this.stopRepository = stopRepository;
        this.assignmentRepository = assignmentRepository;
        this.shipmentRepository = shipmentRepository;
    }

    @Transactional(readOnly = true)
    public List<Route> findAll() {
        return routeRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Route findById(Long id) {
        return routeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Маршрут не найден"));
    }

    public BigDecimal currentWeight(Long routeId) {
        return assignmentRepository.findByStop_Route_Id(routeId).stream()
                .map(a -> a.getShipment().getWeight())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void assertFitsLocked(Long routeId, BigDecimal additionalWeight, Long ignoredAssignmentId) {
        Route route = routeRepository.findByIdForUpdate(routeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Маршрут не найден"));
        BigDecimal used = assignmentRepository.findByStop_Route_Id(routeId).stream()
                .filter(a -> ignoredAssignmentId == null || !ignoredAssignmentId.equals(a.getId()))
                .map(ShipmentAssignment::getShipment)
                .map(Shipment::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = used.add(additionalWeight);
        if (total.compareTo(route.getMaxWeight()) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Масса отправлений " + total + " превышает лимит маршрута " + route.getMaxWeight()
            );
        }
    }

    public Route create(LocalDate routeDate, BigDecimal maxWeight, String status) {
        String normalized = StatusRules.requireRoute(status);
        if (!Statuses.ROUTE_PLANNED.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Новый маршрут только со статусом PLANNED");
        }
        Route route = new Route();
        route.setRouteDate(routeDate);
        route.setMaxWeight(maxWeight);
        route.setStatus(Statuses.ROUTE_PLANNED);
        return routeRepository.save(route);
    }

    public Route update(Long id, LocalDate routeDate, BigDecimal maxWeight, String status) {
        Route route = findById(id);
        String newStatus = StatusRules.requireRoute(status);
        BigDecimal used = currentWeight(id);
        if (used.compareTo(maxWeight) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Новый лимит меньше назначенной массы " + used);
        }
        if (Statuses.ROUTE_IN_PROGRESS.equals(newStatus) || Statuses.ROUTE_COMPLETED.equals(newStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Статусы IN_PROGRESS/COMPLETED — только через start-route / close-route"
            );
        }
        route.setRouteDate(routeDate);
        route.setMaxWeight(maxWeight);
        route.setStatus(newStatus);
        return routeRepository.save(route);
    }

    public void delete(Long id) {
        Route route = findById(id);
        if (Statuses.ROUTE_IN_PROGRESS.equals(route.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя удалить маршрут в работе");
        }
        List<Stop> stops = stopRepository.findByRoute_Id(id);
        if (!stops.isEmpty()) {
            for (ShipmentAssignment link : assignmentRepository.findByStop_Route_Id(id)) {
                if (Statuses.ASSIGNMENT_COMPLETED.equals(link.getStatus())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Есть завершённые доставки");
                }
                Shipment shipment = link.getShipment();
                assignmentRepository.delete(link);
                if (Statuses.SHIPMENT_ASSIGNED.equals(shipment.getStatus())
                        || Statuses.SHIPMENT_IN_TRANSIT.equals(shipment.getStatus())) {
                    shipment.setStatus(Statuses.SHIPMENT_CREATED);
                    shipmentRepository.save(shipment);
                }
            }
            assignmentRepository.flush();
            stopRepository.deleteByRouteId(id);
            stopRepository.flush();
        }
        routeRepository.deleteById(id);
    }
}
