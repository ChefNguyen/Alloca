-- ALC-31: zones are soft-deleted. Soft-deleted resources keep their NOT NULL zone_id
-- (fk_resources_zone is ON DELETE RESTRICT), so a zone that ever held a resource can
-- never be removed physically; deleted_at hides it instead.

ALTER TABLE zones
    ADD COLUMN deleted_at timestamptz;
