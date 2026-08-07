-- The history payload of the REST contract (schema EpisodeDetail) declares
-- `required: [fromStatus, toStatus, at]`, so a transition with no origin is a
-- row the API cannot legally render. The nullable column described a birth
-- transition that nothing writes: an episode's creation is the episodes row
-- itself, and every transition comes from EpisodeService, which always has the
-- status it is moving away from.
--
-- No backfill: for a null to exist, code that never existed would have had to
-- write it. The statement failing would itself be the discovery that something
-- else was writing here.
--
-- Forward-only (ADR-0010).

alter table episode_state_transitions
    alter column from_status set not null;
