package valhalla.core.stock.app.modules.establishments.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.establishments.entity.EstablishmentEntity;
import java.util.UUID;

public interface EstablishmentRepository extends JpaRepository<EstablishmentEntity, UUID> { }
