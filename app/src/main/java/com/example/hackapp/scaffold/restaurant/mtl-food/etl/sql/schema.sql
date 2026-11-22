-- SQLite schema of places.db, the prepackaged database shipped in the Android app.
--
-- It MUST stay identical to what Room generates from the Kotlin entities in
-- app/data/local (table names, column names, affinities, NOT NULL, primary keys,
-- foreign keys and index names). Room validates the file on first open and
-- throws if anything differs. When you change an entity:
--   1. build the app with room.schemaLocation set,
--   2. copy the CREATE statements from app/schemas/.../<version>.json here,
--   3. bump DB_VERSION in the ETL (PRAGMA user_version) and in @Database.
-- Enum columns hold the Kotlin enum constant name (e.g. 'RESTAURANT').

CREATE TABLE IF NOT EXISTS `place` (
  `id` INTEGER NOT NULL,
  `city_id` TEXT NOT NULL,
  `osm_id` TEXT,
  `google_place_id` TEXT,
  `name` TEXT NOT NULL,
  `address` TEXT NOT NULL,
  `lat` REAL NOT NULL,
  `lng` REAL NOT NULL,
  `borough` TEXT,
  `venue_type` TEXT NOT NULL,
  `is_chain` INTEGER NOT NULL,
  `price_min` INTEGER,
  `price_max` INTEGER,
  `price_source` TEXT NOT NULL,
  `description_fr` TEXT,
  `description_en` TEXT,
  `description_source` TEXT NOT NULL,
  `rating` REAL,
  `rating_count` INTEGER,
  `opening_hours` TEXT,
  `photo_ref` TEXT,
  `status` TEXT NOT NULL,
  `status_verified_at` TEXT NOT NULL,
  `enriched_at` TEXT,
  PRIMARY KEY(`id`)
);
CREATE UNIQUE INDEX IF NOT EXISTS `index_place_city_id` ON `place` (`city_id`);
CREATE INDEX IF NOT EXISTS `index_place_lat_lng` ON `place` (`lat`, `lng`);
CREATE INDEX IF NOT EXISTS `index_place_borough` ON `place` (`borough`);

CREATE TABLE IF NOT EXISTS `place_cuisine` (
  `place_id` INTEGER NOT NULL,
  `cuisine` TEXT NOT NULL,
  PRIMARY KEY(`place_id`, `cuisine`),
  FOREIGN KEY(`place_id`) REFERENCES `place`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_place_cuisine_cuisine` ON `place_cuisine` (`cuisine`);

CREATE TABLE IF NOT EXISTS `place_dietary` (
  `place_id` INTEGER NOT NULL,
  `dietary` TEXT NOT NULL,
  PRIMARY KEY(`place_id`, `dietary`),
  FOREIGN KEY(`place_id`) REFERENCES `place`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_place_dietary_dietary` ON `place_dietary` (`dietary`);

CREATE TABLE IF NOT EXISTS `area` (
  `code` TEXT NOT NULL,
  `kind` TEXT NOT NULL,
  `name_fr` TEXT NOT NULL,
  `name_en` TEXT NOT NULL,
  PRIMARY KEY(`code`)
);

CREATE TABLE IF NOT EXISTS `cuisine_group` (
  `code` TEXT NOT NULL,
  `emoji` TEXT NOT NULL,
  `label_fr` TEXT NOT NULL,
  `label_en` TEXT NOT NULL,
  `venue_type` TEXT,
  `sort_order` INTEGER NOT NULL,
  PRIMARY KEY(`code`)
);

CREATE TABLE IF NOT EXISTS `cuisine_group_member` (
  `group_code` TEXT NOT NULL,
  `cuisine` TEXT NOT NULL,
  PRIMARY KEY(`group_code`, `cuisine`),
  FOREIGN KEY(`group_code`) REFERENCES `cuisine_group`(`code`) ON UPDATE NO ACTION ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_cuisine_group_member_cuisine` ON `cuisine_group_member` (`cuisine`);

CREATE TABLE IF NOT EXISTS `price_bracket` (
  `code` TEXT NOT NULL,
  `label` TEXT NOT NULL,
  `max_price` INTEGER,
  `sort_order` INTEGER NOT NULL,
  PRIMARY KEY(`code`)
);

CREATE TABLE IF NOT EXISTS `venue_type_slot` (
  `venue_type` TEXT NOT NULL,
  `slot` TEXT NOT NULL,
  PRIMARY KEY(`venue_type`, `slot`)
);
