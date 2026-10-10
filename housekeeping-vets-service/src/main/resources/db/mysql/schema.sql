CREATE DATABASE IF NOT EXISTS housekeeping;

USE housekeeping;

CREATE TABLE IF NOT EXISTS vets (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  first_name VARCHAR(30),
  last_name VARCHAR(30),
  INDEX(last_name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS specialties (
  id INT(4) UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80),
  INDEX(name)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS vet_specialties (
  vet_id INT(4) UNSIGNED NOT NULL,
  specialty_id INT(4) UNSIGNED NOT NULL,
  FOREIGN KEY (vet_id) REFERENCES vets(id),
  FOREIGN KEY (specialty_id) REFERENCES specialties(id),
  UNIQUE (vet_id,specialty_id)
) engine=InnoDB;
CREATE TABLE IF NOT EXISTS service_skills (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS housekeepers (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(80) NOT NULL,
  telephone VARCHAR(20) NOT NULL,
  introduction VARCHAR(500),
  experience_years INT,
  rating DECIMAL(2,1),
  status VARCHAR(30),
  service_district VARCHAR(120)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS housekeeper_skills (
  housekeeper_id INT UNSIGNED NOT NULL,
  skill_id INT UNSIGNED NOT NULL,
  FOREIGN KEY (housekeeper_id) REFERENCES housekeepers(id),
  FOREIGN KEY (skill_id) REFERENCES service_skills(id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS service_items (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  description VARCHAR(500),
  skill_id INT UNSIGNED NOT NULL,
  pricing_type VARCHAR(30),
  unit_price DECIMAL(10,2),
  minimum_units DECIMAL(10,2),
  estimated_minutes INT,
  enabled BOOLEAN,
  FOREIGN KEY (skill_id) REFERENCES service_skills(id)
) engine=InnoDB;

CREATE TABLE IF NOT EXISTS worker_schedules (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  housekeeper_id INT UNSIGNED NOT NULL,
  service_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  status VARCHAR(30),
  reservation_key VARCHAR(80) UNIQUE,
  FOREIGN KEY (housekeeper_id) REFERENCES housekeepers(id)
) engine=InnoDB;
