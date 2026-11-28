package valhalla.core.stock.app.modules.establishments.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import java.util.Optional;
import java.util.UUID;

public interface EstablishmentRepository extends JpaRepository<EstablishmentEntity, UUID> {

    boolean existsByCnpjAndIdNot(String cnpj, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EstablishmentEntity e where e.id = :id")
    Optional<EstablishmentEntity> findByIdForUpdate(@Param("id") UUID id);
}
