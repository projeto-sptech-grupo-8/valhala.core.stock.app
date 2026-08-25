package valhalla.core.stock.app.modules.accesscontrol.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.accesscontrol.entity.ProfileEntity;

import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<ProfileEntity, UUID> {

    Optional<ProfileEntity> findByNameIgnoreCase(String name);
}
