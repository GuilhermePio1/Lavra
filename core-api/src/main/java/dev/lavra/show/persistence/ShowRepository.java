package dev.lavra.show.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Ownership is part of every signature: there is no lookup by id alone, so the
 * query that would serve another user's show cannot be written by accident
 * (ADR-0014). A miss is a miss — the caller answers 404 either way, which is
 * what makes someone else's show indistinguishable from a non-existent one.
 *
 * <p>That is why this extends {@link Repository} and not {@code JpaRepository}:
 * the convenient base would inherit {@code findById}, {@code getReferenceById}
 * and {@code findAll}, and the guarantee above would be a comment rather than a
 * compiler error. The four methods below are the whole surface — {@code save}
 * and {@code delete} are declared, not inherited, and Spring Data routes them
 * to its standard implementation all the same.
 */
public interface ShowRepository extends Repository<ShowEntity, UUID> {

    List<ShowEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<ShowEntity> findByIdAndUserId(UUID id, UUID userId);

    ShowEntity save(ShowEntity show);

    void delete(ShowEntity show);
}
