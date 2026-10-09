-- Match the owner filters and deterministic ORDER BY used by paginated lists.
CREATE INDEX customers_active_recent_idx ON customers(owner_id,updated_at DESC,id) WHERE NOT deleted;
CREATE INDEX sites_active_recent_idx ON sites(owner_id,updated_at DESC,id) WHERE NOT deleted;
CREATE INDEX sites_active_customer_recent_idx ON sites(owner_id,customer_id,updated_at DESC,id) WHERE NOT deleted;
CREATE INDEX notes_active_recent_idx ON field_notes(owner_id,updated_at DESC,id) WHERE NOT deleted;
CREATE INDEX notes_active_site_recent_idx ON field_notes(owner_id,site_id,updated_at DESC,id) WHERE NOT deleted;
CREATE INDEX notes_active_status_recent_idx ON field_notes(owner_id,status,updated_at DESC,id) WHERE NOT deleted;
CREATE INDEX notes_active_site_status_recent_idx ON field_notes(owner_id,site_id,status,updated_at DESC,id) WHERE NOT deleted;

-- pg_trgm supports indexed literal substring searches through escaped ILIKE.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX customers_active_name_search_idx ON customers USING gin(name gin_trgm_ops) WHERE NOT deleted;
CREATE INDEX sites_active_name_search_idx ON sites USING gin(site_name gin_trgm_ops) WHERE NOT deleted;
CREATE INDEX notes_active_title_search_idx ON field_notes USING gin(title gin_trgm_ops) WHERE NOT deleted;
CREATE INDEX notes_active_description_search_idx ON field_notes USING gin(description gin_trgm_ops) WHERE NOT deleted;
