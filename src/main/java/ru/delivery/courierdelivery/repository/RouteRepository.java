package ru.delivery.courierdelivery.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.delivery.courierdelivery.model.Route;

public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findAllByOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Route r where r.id = :id")
    Optional<Route> findByIdForUpdate(@Param("id") Long id);
}
