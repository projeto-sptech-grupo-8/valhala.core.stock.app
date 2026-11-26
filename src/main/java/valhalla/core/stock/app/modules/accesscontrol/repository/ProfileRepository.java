package valhalla.core.stock.app.modules.accesscontrol.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<ProfileEntity, Integer> {

    @EntityGraph(attributePaths = "functionalities")
    Optional<ProfileEntity> findByEstablishment_IdAndNameIgnoreCase(
            UUID establishmentId, String name
    );

    @EntityGraph(attributePaths = "functionalities")
    List<ProfileEntity> findAllByEstablishment_IdOrderByNameAsc(UUID establishmentId);
}
