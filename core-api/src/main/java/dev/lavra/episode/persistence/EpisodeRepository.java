package dev.lavra.episode.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * As in {@code ShowRepository}, there is no lookup by id alone: extending
 * {@link Repository} instead of {@code JpaRepository} keeps {@code findById} off
 * the surface, so a query that could serve another user's episode cannot be
 * written by accident (ADR-0014).
 *
 * <p>Ownership of an episode is one hop away — the user owns the show, the show
 * owns the episode — and the join is written in SQL rather than reached through
 * a mapped association. An association would put {@code ShowEntity} inside this
 * slice's object graph, and entities stay in the feature that owns them
 * (ADR-0013); the {@code shows} table is a table, and joining it is not the same
 * as importing the show slice.
 */
public interface EpisodeRepository extends Repository<EpisodeEntity, UUID> {

    @Query(value = """
            select e.*
            from episodes e
            join shows s on s.id = e.show_id
            where e.id = :episodeId and s.user_id = :userId
            """, nativeQuery = true)
    Optional<EpisodeEntity> findOwned(@Param("episodeId") UUID episodeId, @Param("userId") UUID userId);

    EpisodeEntity save(EpisodeEntity episode);
}
