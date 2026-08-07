package dev.lavra.episode.persistence;

import dev.lavra.episode.EpisodePage;
import dev.lavra.episode.EpisodeSummary;
import dev.lavra.episode.domain.EpisodeStatus;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The listing side of the slice, in native SQL (ADR-0014).
 *
 * <p>Two optional filters plus pagination is where derived queries stop paying
 * off: four method signatures for the same question, or a Specification that
 * hides the SQL without removing it. One statement with the predicates appended
 * is both shorter and readable as the query the database actually runs.
 *
 * <p>The join on {@code shows} is the ownership filter, and it is not optional
 * — every query here starts from the user.
 */
@Repository
public class EpisodeQueries {

    private static final String FROM_OWNED_EPISODES = """
            from episodes e
            join shows s on s.id = e.show_id
            where s.user_id = :userId
            """;

    private final JdbcClient jdbcClient;

    EpisodeQueries(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * A page of the user's episodes, most recent first.
     *
     * @param showId restricts to one show; null lists across every show
     * @param status restricts to one state; null lists every state
     */
    public EpisodePage page(UUID userId, UUID showId, EpisodeStatus status, int page, int size) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);

        List<String> filters = new ArrayList<>();
        if (showId != null) {
            filters.add("and e.show_id = :showId");
            params.put("showId", showId);
        }
        if (status != null) {
            filters.add("and e.status = :status");
            params.put("status", status.name());
        }
        String predicates = String.join("\n", filters);

        long totalItems = jdbcClient
                .sql("select count(*) " + FROM_OWNED_EPISODES + predicates)
                .params(params)
                .query(Long.class)
                .single();

        // The count is cheap and the page is not: an out-of-range page has
        // nothing to fetch, and the frontend still needs the total to correct
        // itself.
        if (totalItems == 0) {
            return new EpisodePage(List.of(), page, 0);
        }

        List<EpisodeSummary> items = jdbcClient
                .sql("""
                        select e.id, e.working_title, e.status, e.duration_seconds,
                               e.created_at, e.updated_at
                        """ + FROM_OWNED_EPISODES + predicates + """

                        order by e.created_at desc, e.id desc
                        limit :size offset :offset
                        """)
                .params(params)
                .param("size", size)
                .param("offset", (long) page * size)
                .query((rs, rowNum) -> new EpisodeSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("working_title"),
                        EpisodeStatus.valueOf(rs.getString("status")),
                        rs.getObject("duration_seconds", Integer.class),
                        // timestamptz comes back as OffsetDateTime; the driver
                        // does not map Instant in either direction.
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("updated_at", OffsetDateTime.class).toInstant()))
                .list();

        return new EpisodePage(items, page, totalItems);
    }
}
