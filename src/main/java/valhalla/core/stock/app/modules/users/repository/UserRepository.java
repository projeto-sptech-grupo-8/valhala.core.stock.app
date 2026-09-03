package valhalla.core.stock.app.modules.users.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    boolean existsByIdAndEmailIgnoreCase(UUID id, String email);

    boolean existsByIdAndProfile_NameIgnoreCase(UUID id, String profileName);

    boolean existsByEmailIgnoreCaseAndProfile_NameIgnoreCase(
            String email,
            String profileName
    );
}
