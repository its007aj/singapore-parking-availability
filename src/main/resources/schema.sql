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
