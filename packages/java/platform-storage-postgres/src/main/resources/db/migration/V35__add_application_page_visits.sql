ALTER TABLE landing_page_visits
  ADD COLUMN app text NOT NULL DEFAULT 'landing';

CREATE INDEX landing_page_visits_app_occurred_idx
  ON landing_page_visits (app, occurred_at DESC);
