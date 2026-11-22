package valhalla.core.stock.app.modules.users.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import valhalla.core.stock.app.modules.users.entity.UserAccessHistoryEntity;

import java.util.List;
import java.util.UUID;

public interface UserAccessHistoryRepository extends JpaRepository<UserAccessHistoryEntity, Long> {

    List<UserAccessHistoryEntity> findByUser_IdOrderByOccurredAtDescIdDesc(UUID userId, Pageable pageable);
}
