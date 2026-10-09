CREATE TABLE users (
 id UUID PRIMARY KEY,
 email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE sessions (
 token_hash CHAR(64) PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES users(id),
 expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX sessions_user_idx ON sessions(user_id);
CREATE TABLE photos (
 id UUID PRIMARY KEY,
 owner_id UUID NOT NULL REFERENCES users(id),
 content_type VARCHAR(30) NOT NULL,
 bytes BYTEA NOT NULL CHECK(octet_length(bytes) BETWEEN 1 AND 5242880),
 digest CHAR(64) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(id, owner_id)
);
CREATE TABLE customers (
 id UUID PRIMARY KEY,
 owner_id UUID NOT NULL REFERENCES users(id),
 name VARCHAR(200) NOT NULL,
 contact_information VARCHAR(2000) NOT NULL DEFAULT '',
 version BIGINT NOT NULL CHECK(version>0),
 deleted BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(id,owner_id)
);
CREATE TABLE sites (
 id UUID PRIMARY KEY,
 owner_id UUID NOT NULL REFERENCES users(id),
 customer_id UUID NOT NULL,
 site_name VARCHAR(200) NOT NULL,
 address VARCHAR(2000) NOT NULL,
 version BIGINT NOT NULL CHECK(version>0),
 deleted BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 UNIQUE(id,owner_id),
 FOREIGN KEY(customer_id,owner_id) REFERENCES customers(id,owner_id)
);
CREATE TABLE field_notes (
 id UUID PRIMARY KEY,
 owner_id UUID NOT NULL REFERENCES users(id),
 site_id UUID NOT NULL,
 title VARCHAR(200) NOT NULL,
 description VARCHAR(10000) NOT NULL DEFAULT '',
 latitude DOUBLE PRECISION,
 longitude DOUBLE PRECISION,
 occurred_at TIMESTAMPTZ NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('DRAFT','OPEN','IN_PROGRESS','COMPLETED')),
 photo_id UUID,
 version BIGINT NOT NULL CHECK(version>0),
 deleted BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 FOREIGN KEY(site_id,owner_id) REFERENCES sites(id,owner_id),
 FOREIGN KEY(photo_id,owner_id) REFERENCES photos(id,owner_id),
 CHECK((latitude IS NULL AND longitude IS NULL) OR
 (latitude IS NOT NULL AND longitude IS NOT NULL AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180))
);
CREATE INDEX customers_owner_idx ON customers(owner_id,deleted);
CREATE INDEX sites_owner_parent_idx ON sites(owner_id,customer_id,deleted);
CREATE INDEX notes_owner_site_idx ON field_notes(owner_id,site_id,deleted);
CREATE INDEX notes_owner_status_idx ON field_notes(owner_id,status,deleted);
CREATE TABLE changes (
 owner_id UUID NOT NULL REFERENCES users(id),
 revision BIGINT NOT NULL,
 entity_type VARCHAR(20) NOT NULL,
 entity_id UUID NOT NULL,
 snapshot TEXT NOT NULL,
 PRIMARY KEY(owner_id,revision)
);
CREATE TABLE mutation_receipts (
 owner_id UUID NOT NULL REFERENCES users(id),
 mutation_id UUID NOT NULL,
 request_hash CHAR(64) NOT NULL,
 response TEXT NOT NULL,
 PRIMARY KEY(owner_id,mutation_id)
);
