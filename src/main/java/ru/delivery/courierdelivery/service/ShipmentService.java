package ru.delivery.courierdelivery.service;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.delivery.courierdelivery.domain.StatusRules;
import ru.delivery.courierdelivery.domain.Statuses;
import ru.delivery.courierdelivery.model.Shipment;
import ru.delivery.courierdelivery.model.ShipmentAssignment;
import ru.delivery.courierdelivery.model.User;
import ru.delivery.courierdelivery.repository.ShipmentAssignmentRepository;
import ru.delivery.courierdelivery.repository.ShipmentRepository;
import ru.delivery.courierdelivery.repository.UserRepository;

@Service
@Transactional
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;
    private final ShipmentAssignmentRepository assignmentRepository;

    public ShipmentService(
            ShipmentRepository shipmentRepository,
            UserRepository userRepository,
            ShipmentAssignmentRepository assignmentRepository
    ) {
        this.shipmentRepository = shipmentRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional(readOnly = true)
    public List<Shipment> findAll() {
        return shipmentRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public Shipment findById(Long id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Отправление не найдено"));
    }

    public Shipment create(Long senderId, String address, BigDecimal weight, String status) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Отправитель не найден"));
        String normalized = StatusRules.requireShipment(status);
        if (!Statuses.SHIPMENT_CREATED.equals(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Новое отправление можно создать только со статусом CREATED"
            );
        }
        Shipment shipment = new Shipment();
        shipment.setSender(sender);
        shipment.setAddress(address.trim());
        shipment.setWeight(weight);
        shipment.setStatus(Statuses.SHIPMENT_CREATED);
        return shipmentRepository.save(shipment);
    }

    public Shipment update(Long id, Long senderId, String address, BigDecimal weight, String status) {
        Shipment shipment = findById(id);
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Отправитель не найден"));
        String newStatus = StatusRules.requireShipment(status);
        assertStatusChangeAllowed(shipment, newStatus);
        shipment.setSender(sender);
        shipment.setAddress(address.trim());
        shipment.setWeight(weight);
        shipment.setStatus(newStatus);
        return shipmentRepository.save(shipment);
    }

    public void delete(Long id) {
        Shipment shipment = findById(id);
        if (Statuses.SHIPMENT_DELIVERED.equals(shipment.getStatus())
                || Statuses.SHIPMENT_IN_TRANSIT.equals(shipment.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Нельзя удалить отправление в статусе " + shipment.getStatus()
            );
        }
        assignmentRepository.deleteByShipmentId(id);
        assignmentRepository.flush();
        shipmentRepository.deleteById(id);
    }

    private void assertStatusChangeAllowed(Shipment shipment, String newStatus) {
        String current = shipment.getStatus();
        if (current.equals(newStatus)) {
            return;
        }
        List<ShipmentAssignment> links = assignmentRepository.findByShipment_Id(shipment.getId());
        boolean hasActive = links.stream().anyMatch(a -> StatusRules.isActiveAssignment(a.getStatus()));
        boolean hasCompleted = links.stream()
                .anyMatch(a -> Statuses.ASSIGNMENT_COMPLETED.equals(a.getStatus()));

        if (Statuses.SHIPMENT_DELIVERED.equals(newStatus) && !hasCompleted) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Статус DELIVERED только после complete-delivery"
            );
        }
        if (Statuses.SHIPMENT_ASSIGNED.equals(newStatus) && !hasActive && !hasCompleted) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "ASSIGNED допустим только при наличии назначения"
            );
        }
        if (Statuses.SHIPMENT_IN_TRANSIT.equals(newStatus) && !hasActive) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "IN_TRANSIT допустим только при активном назначении"
            );
        }
        if (Statuses.SHIPMENT_CREATED.equals(newStatus) && (hasActive || hasCompleted)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Нельзя вернуть CREATED, пока есть назначение"
            );
        }
        if (Statuses.SHIPMENT_CANCELLED.equals(newStatus) && hasCompleted) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Нельзя отменить уже доставленное отправление"
            );
        }
    }
}
