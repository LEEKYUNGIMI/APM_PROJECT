CREATE ALIAS IF NOT EXISTS SLEEP FOR 'com.leekyungmi.apm.simple.db.H2Functions.sleep';

CREATE TABLE IF NOT EXISTS products (
    id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name  VARCHAR(100) NOT NULL,
    price INT          NOT NULL
);
