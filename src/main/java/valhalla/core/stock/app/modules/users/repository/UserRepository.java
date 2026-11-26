package valhalla.core.stock.app.modules.users.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.users.entity.UserEntity;

import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Sort;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<UserEntity> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {
            "establishment",
            "profile",
            "profile.functionalities",
            "functionalityOverrides",
            "functionalityOverrides.functionality"
    })
    Optional<UserEntity> findWithAccessGraphById(UUID id);

    @EntityGraph(attributePaths = {
            "establishment",
            "profile",
            "profile.functionalities",
            "functionalityOverrides",
            "functionalityOverrides.functionality"
    })
    Optional<UserEntity> findWithAccessGraphByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {
            "establishment",
            "profile",
            "profile.functionalities",
            "functionalityOverrides",
            "functionalityOverrides.functionality"
    })
    List<UserEntity> findAllByEstablishment_Id(UUID establishmentId, Sort sort);

    @EntityGraph(attributePaths = {
            "establishment",
            "profile",
            "profile.functionalities",
            "functionalityOverrides",
            "functionalityOverrides.functionality"
    })
    List<UserEntity> findAllByEstablishment_Id(UUID establishmentId);

    List<UserEntity> findAllByProfile_Id(Integer profileId);

    boolean existsByIdAndEmailIgnoreCase(UUID id, String email);

    boolean existsByIdAndEstablishment_Id(UUID id, UUID establishmentId);

    boolean existsByIdAndProfile_NameIgnoreCase(UUID id, String profileName);

    boolean existsByEmailIgnoreCaseAndProfile_NameIgnoreCase(
            String email,
            String profileName
    );
}
