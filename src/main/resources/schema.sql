CREATE TABLE IF NOT EXISTS car_park (
    car_park_no             VARCHAR(10) PRIMARY KEY,
    address                 VARCHAR(255) NOT NULL,
    latitude                DOUBLE PRECISION NOT NULL,
    longitude               DOUBLE PRECISION NOT NULL,
    car_park_type           VARCHAR(50),
    type_of_parking_system  VARCHAR(50),
    short_term_parking      VARCHAR(50),
    free_parking            VARCHAR(50),
    night_parking           VARCHAR(10),
    car_park_decks          INTEGER,
    gantry_height           DOUBLE PRECISION,
    car_park_basement       VARCHAR(1),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- No foreign key to car_park: the availability feed covers HDB, URA and LTA car parks,
-- while car_park (from the HDB static dataset) only covers HDB ones. Rows for non-HDB
-- car park numbers are expected and are simply excluded when joined for nearby search.
CREATE TABLE IF NOT EXISTS car_park_availability (
    car_park_no     VARCHAR(10) NOT NULL,
    lot_type        VARCHAR(5) NOT NULL,
    total_lots      INTEGER NOT NULL,
    lots_available  INTEGER NOT NULL,
    lot_updated_at  TIMESTAMPTZ NOT NULL,
    fetched_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (car_park_no, lot_type)
);
