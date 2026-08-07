-- The upload flow of spec 0001: the client declares what it is about to send,
-- and upload-complete checks the blob that landed against that declaration.
-- Without these columns the check has nothing to compare against, and
-- episode.uploaded.v1 has no originalFilename/contentType to carry.
--
-- Forward-only (ADR-0010).

-- Not null: the only way an episode row comes into existence is the constructor
-- that takes a validated UploadDeclaration, so an episode without a declaration
-- is a state the code cannot produce — the schema should not allow it either.
alter table episodes
    add column original_filename text   not null,
    add column content_type      text   not null,
    add column size_bytes        bigint not null
        constraint episodes_size_bytes_positive check (size_bytes > 0);

-- Same reason as V2 renamed shows.title: the REST contract calls this field
-- `workingTitle` (schema Episode), and one vocabulary from the API down to the
-- table beats mapping around the difference in the entity.
alter table episodes rename column title to working_title;
