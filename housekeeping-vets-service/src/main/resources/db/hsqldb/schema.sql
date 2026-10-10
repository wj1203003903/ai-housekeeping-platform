DROP TABLE vet_specialties IF EXISTS;
DROP TABLE vets IF EXISTS;
DROP TABLE specialties IF EXISTS;
DROP TABLE worker_schedules IF EXISTS;
DROP TABLE housekeeper_skills IF EXISTS;
DROP TABLE service_items IF EXISTS;
DROP TABLE housekeepers IF EXISTS;
DROP TABLE service_skills IF EXISTS;

CREATE TABLE vets (
  id         INTEGER IDENTITY PRIMARY KEY,
  first_name VARCHAR(30),
  last_name  VARCHAR(30)
);
CREATE INDEX vets_last_name ON vets (last_name);

CREATE TABLE specialties (
  id   INTEGER IDENTITY PRIMARY KEY,
  name VARCHAR(80)
);
CREATE INDEX specialties_name ON specialties (name);

CREATE TABLE vet_specialties (
  vet_id       INTEGER NOT NULL,
  specialty_id INTEGER NOT NULL
);
ALTER TABLE vet_specialties ADD CONSTRAINT fk_vet_specialties_vets FOREIGN KEY (vet_id) REFERENCES vets (id);
ALTER TABLE vet_specialties ADD CONSTRAINT fk_vet_specialties_specialties FOREIGN KEY (specialty_id) REFERENCES specialties (id);

CREATE TABLE service_skills (
  id INTEGER IDENTITY PRIMARY KEY,
  name VARCHAR(80) NOT NULL
);

CREATE TABLE housekeepers (
  id INTEGER IDENTITY PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  telephone VARCHAR(20) NOT NULL,
  introduction VARCHAR(500),
  experience_years INTEGER,
  rating DECIMAL(2,1),
  status VARCHAR(30),
  service_district VARCHAR(120)
);

CREATE TABLE housekeeper_skills (
  housekeeper_id INTEGER NOT NULL,
  skill_id INTEGER NOT NULL,
  FOREIGN KEY (housekeeper_id) REFERENCES housekeepers(id),
  FOREIGN KEY (skill_id) REFERENCES service_skills(id)
);

CREATE TABLE service_items (
  id INTEGER IDENTITY PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  skill_id INTEGER NOT NULL,
  pricing_type VARCHAR(30),
  unit_price DECIMAL(10,2),
  minimum_units DECIMAL(10,2),
  estimated_minutes INTEGER,
  enabled BOOLEAN,
  FOREIGN KEY (skill_id) REFERENCES service_skills(id)
);

CREATE TABLE worker_schedules (
  id INTEGER IDENTITY PRIMARY KEY,
  housekeeper_id INTEGER NOT NULL,
  service_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  status VARCHAR(30),
  reservation_key VARCHAR(80) UNIQUE,
  FOREIGN KEY (housekeeper_id) REFERENCES housekeepers(id)
);
