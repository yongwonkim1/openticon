package io.ssafy.openticon.repository;

import io.ssafy.openticon.dto.ReportStateType;
import io.ssafy.openticon.entity.EmoticonPackEntity;
import io.ssafy.openticon.entity.MemberEntity;
import io.ssafy.openticon.entity.ObjectionEntity;
import io.ssafy.openticon.entity.PointHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ObjectionRepository extends JpaRepository<ObjectionEntity, Long> {

    @Query("SELECT o FROM ObjectionEntity o WHERE o.emoticonPack = :emoticonPack AND o.state = :state")
    Optional<ObjectionEntity> findByEmoticonPackAndState(
            @Param("emoticonPack") EmoticonPackEntity emoticonPack,
            @Param("state") ReportStateType state
    );

    Page<ObjectionEntity> findByMember(MemberEntity member, Pageable pageable);

    @Query("SELECT o FROM ObjectionEntity o WHERE o.state = :state")
    Page<ObjectionEntity> findByState(@Param("state") ReportStateType state, Pageable pageable);

}
