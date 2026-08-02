-- The REST contract calls this field `name` (schema Show in
-- contracts/openapi/core-api.v1.yaml); V1 created it as `title`. Renaming the
-- column instead of mapping around it in the entity keeps one vocabulary from
-- the API down to the table.
--
-- Forward-only (ADR-0010): a rename, not an edit of V1.

alter table shows rename column title to name;
