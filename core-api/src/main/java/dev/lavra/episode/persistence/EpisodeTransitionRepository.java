package dev.lavra.episode.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * The history table is append-and-read: a transition is a fact that happened,
 * so nothing here updates or deletes one.
 */
public interface EpisodeTransitionRepository extends Repository<EpisodeStateTransitionEntity, Long> {

    List<EpisodeStateTransitionEntity> findByEpisodeIdOrderByOccurredAtAsc(UUID episodeId);

    EpisodeStateTransitionEntity save(EpisodeStateTransitionEntity transition);
}
