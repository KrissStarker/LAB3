package ru.delivery.courierdelivery.service;

import java.util.List;
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
public class StopService {

    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final ShipmentAssignmentRepository assignmentRepository;
    private final ShipmentRepository shipmentRepository;

    public StopService(
            StopRepository stopRepository,
            RouteRepository routeRepository,
            ShipmentAssignmentRepository assignmentRepository,
            ShipmentRepository shipmentRepository
    ) {
        this.stopRepository = stopRepository;
        this.routeRepository = routeRepository;
        this.assignmentRepository = assignmentRepository;
        this.shipmentRepository = shipmentRepository;
    }

    @Transactional(readOnly = true)
    public List<Stop> findAll() {
        return stopRepository.findAllByOrderBySequenceNumberAsc();
    }

    @Transactional(readOnly = true)
    public Stop findById(Long id) {
        return stopRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Остановка не найдена"));
    }

    public Stop create(Long routeId, String address, Integer sequenceNumber) {
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Маршрут не найден"));
        if (Statuses.ROUTE_COMPLETED.equals(route.getStatus())
                || Statuses.ROUTE_CANCELLED.equals(route.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Нельзя добавлять остановки к маршруту в статусе " + route.getStatus()
            );
        }
        stopRepository.findByRoute_IdAndSequenceNumber(routeId, sequenceNumber).ifPresent(s -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Номер остановки на маршруте уже занят");
        });
        Stop stop = new Stop();
        stop.setRoute(route);
        stop.setAddress(address.trim());
        stop.setSequenceNumber(sequenceNumber);
        return stopRepository.save(stop);
    }

    public Stop update(Long id, Long routeId, String address, Integer sequenceNumber) {
        Stop stop = findById(id);
        Route route = routeRepository.findById(routeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Маршрут не найден"));
        stopRepository.findByRoute_IdAndSequenceNumber(routeId, sequenceNumber)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(s -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Номер остановки на маршруте уже занят");
                });
        stop.setRoute(route);
        stop.setAddress(address.trim());
        stop.setSequenceNumber(sequenceNumber);
        return stopRepository.save(stop);
    }

    public void delete(Long id) {
        if (!stopRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Остановка не найдена");
        }
        List<ShipmentAssignment> links = assignmentRepository.findByStop_Id(id);
        for (ShipmentAssignment link : links) {
            if (Statuses.ASSIGNMENT_COMPLETED.equals(link.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Нельзя удалить остановку: есть завершённые назначения"
                );
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
        stopRepository.deleteById(id);
    }
}
