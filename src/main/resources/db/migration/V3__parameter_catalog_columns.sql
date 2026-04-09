-- V3: Add area and enabled columns to parameter table for catalog management
ALTER TABLE parameter ADD COLUMN area VARCHAR(255);
ALTER TABLE parameter ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;
