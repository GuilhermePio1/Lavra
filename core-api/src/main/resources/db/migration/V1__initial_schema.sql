-- Initial schema for the lavra core-api.
--
-- Covers the whole ownership model up front (spec 0004): identity, plans and
-- entitlements exist from the foundation, otherwise the data model is born
-- without an owner. Episode tables land here too because the usage ledger
-- references them and /me counts shows.
--
-- Flyway, plain SQL, forward-only (ADR-0010).

-- ---------------------------------------------------------------------------
-- Plans: catalogue, not code. Changing a plan limit is an UPDATE, not a deploy.
-- ---------------------------------------------------------------------------
create table plans (
    code                      text primary key,
    name                      text        not null,
    monthly_processed_minutes integer     not null check (monthly_processed_minutes >= 0),
    max_shows                 integer     not null check (max_shows >= 0),
    created_at                timestamptz not null default now()
);

insert into plans (code, name, monthly_processed_minutes, max_shows) values
    ('FREE',    'Free',    90,   1),
    ('CREATOR', 'Creator', 600,  1),
    ('STUDIO',  'Studio',  1800, 3);

-- ---------------------------------------------------------------------------
-- Users: keyed by the oid/sub of the OIDC token, created just-in-time on the
-- first authenticated request.
-- ---------------------------------------------------------------------------
create table users (
    id           uuid primary key,
    external_id  text        not null unique,
    email        text        not null,
    display_name text,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Subscriptions: one active plan per user, plus the current billing period.
-- Simple monthly roll-over, no carry-over of unused minutes.
-- ---------------------------------------------------------------------------
create table subscriptions (
    id           uuid primary key,
    user_id      uuid        not null unique references users (id) on delete cascade,
    plan_code    text        not null references plans (code),
    period_start timestamptz not null,
    period_end   timestamptz not null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    constraint subscriptions_period_order check (period_end > period_start)
);

-- ---------------------------------------------------------------------------
-- Shows: the aggregate that owns episodes and carries the voice profile.
-- ---------------------------------------------------------------------------
create table shows (
    id            uuid primary key,
    user_id       uuid        not null references users (id) on delete cascade,
    title         text        not null,
    description   text,
    voice_profile jsonb       not null default '{}'::jsonb,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

create index shows_user_id_idx on shows (user_id);

-- ---------------------------------------------------------------------------
-- Episodes: state machine of spec 0001. Postgres is the source of truth.
-- ---------------------------------------------------------------------------
create table episodes (
    id                   uuid primary key,
    show_id              uuid        not null references shows (id) on delete cascade,
    title                text,
    status               text        not null check (status in (
                             'PENDING_UPLOAD', 'RECEIVED', 'AUDIO_PROCESSING',
                             'TRANSCRIBING', 'GENERATING', 'IN_REVIEW',
                             'READY', 'FAILED')),
    source_blob_path     text,
    processed_blob_path  text,
    transcript_blob_path text,
    duration_seconds     integer check (duration_seconds >= 0),
    failed_stage         text,
    error_code           text,
    error_message        text,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now()
);

create index episodes_show_id_idx on episodes (show_id);
create index episodes_status_idx on episodes (status);

-- History of every transition: feeds the tracking UI and debugging.
create table episode_state_transitions (
    id          bigint generated always as identity primary key,
    episode_id  uuid        not null references episodes (id) on delete cascade,
    from_status text,
    to_status   text        not null,
    reason      text,
    occurred_at timestamptz not null default now()
);

create index episode_state_transitions_episode_id_idx
    on episode_state_transitions (episode_id, occurred_at);

-- ---------------------------------------------------------------------------
-- Usage ledger: immutable debits of processed minutes. Period consumption is
-- the sum of the debits in the period — never an overwritten counter.
--
-- The unique constraint is what makes reprocessing a FAILED episode free:
-- the debit is idempotent by (episode, period), per spec 0004.
-- ---------------------------------------------------------------------------
create table usage_ledger (
    id           bigint generated always as identity primary key,
    user_id      uuid        not null references users (id) on delete cascade,
    episode_id   uuid        not null references episodes (id) on delete cascade,
    minutes      integer     not null check (minutes > 0),
    period_start timestamptz not null,
    recorded_at  timestamptz not null default now(),
    constraint usage_ledger_episode_period_unique unique (episode_id, period_start)
);

create index usage_ledger_user_period_idx on usage_ledger (user_id, period_start);
