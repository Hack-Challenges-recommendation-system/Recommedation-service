-- array_to_string is STABLE, so it can't feed a generated column directly. For text[] it never changes its output.
CREATE FUNCTION immutable_array_to_string(text[], text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE
    AS 'SELECT array_to_string($1, $2)';

-- Educational materials from the case data. id is the material id from the source data.
CREATE TABLE material(
    id               VARCHAR(64)   PRIMARY KEY,
    title            VARCHAR(300)  NOT NULL,
    summary          TEXT,
    format           VARCHAR(32),
    duration_minutes INTEGER,
    level            VARCHAR(16),
    topics           TEXT[]        NOT NULL DEFAULT '{}',
    business_stages  TEXT[]        NOT NULL DEFAULT '{}',
    spheres          TEXT[]        NOT NULL DEFAULT '{}',
    paid             BOOLEAN       NOT NULL DEFAULT FALSE,
    url              VARCHAR(1000) NOT NULL,
    -- Russian full-text search: title weighs most, then topics, then summary.
    search_vector    TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('russian', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('russian', immutable_array_to_string(topics, ' ')), 'B') ||
        setweight(to_tsvector('russian', coalesce(summary, '')), 'C')
    ) STORED
);

CREATE INDEX material_search_vector_idx ON material USING GIN (search_vector);
CREATE INDEX material_business_stages_idx ON material USING GIN (business_stages);
CREATE INDEX material_spheres_idx ON material USING GIN (spheres);
