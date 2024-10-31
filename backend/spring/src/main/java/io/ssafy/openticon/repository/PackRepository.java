package io.ssafy.openticon.repository;

import io.ssafy.openticon.dto.EmoticonPack;
import io.ssafy.openticon.entity.EmoticonPackEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PackRepository extends JpaRepository<EmoticonPackEntity,Long> {
    public EmoticonPackEntity findByShareLink(String uuid);

    Page<EmoticonPackEntity> findByTitleContaining(String title, Pageable pageable);

    @Query("SELECT ep FROM EmoticonPackEntity ep JOIN ep.tagLists tl JOIN tl.tag t WHERE t.tagName = :query")
    Page<EmoticonPackEntity> findByTag(@Param("query") String query, Pageable pageable);

    @Query("SELECT ep FROM EmoticonPackEntity ep JOIN ep.member m WHERE m.nickname LIKE %:query%")
    Page<EmoticonPackEntity> findByAuthorContaining(@Param("query") String query, Pageable pageable);
}
