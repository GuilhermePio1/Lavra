package dev.lavra.show.persistence;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Episode counts for the {@code Show} payload. Native SQL (ADR-0014): these are
 * aggregates, and counting through the ORM would mean either mapping episodes
 * the show layer has no business loading, or one query per show in the list.
 */
@Repository
public class ShowQueries {

    private final JdbcClient jdbcClient;

    ShowQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Episodes per show for one user, in a single query — the list endpoint
     * needs a count next to every show, and an N+1 is the obvious trap here.
     * Shows with no episodes are included, with a count of zero.
     */
    public Map<UUID, Long> episodeCountsByShow(UUID userId) {
        return jdbcClient
                .sql("""
                        select s.id as show_id, count(e.id) as episode_count
                        from shows s
                        left join episodes e on e.show_id = s.id
                        where s.user_id = :userId
                        group by s.id
                        """)
                .param("userId", userId)
                .query((rs, rowNum) -> Map.entry(rs.getObject("show_id", UUID.class), rs.getLong("episode_count")))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public long countEpisodes(UUID showId) {
        return jdbcClient
                .sql("select count(*) from episodes where show_id = :showId")
                .param("showId", showId)
                .query(Long.class)
                .single();
    }

    /** Asks whether any episode exists, rather than counting the ones nobody reads. */
    public boolean hasEpisodes(UUID showId) {
        return jdbcClient
                .sql("select exists(select 1 from episodes where show_id = :showId)")
                .param("showId", showId)
                .query(Boolean.class)
                .single();
    }
}
