CREATE DATABASE IF NOT EXISTS hrs_booking;
USE hrs_booking;

-- Create the hotels table
CREATE TABLE IF NOT EXISTS hotels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(255) NOT NULL,
    address VARCHAR(255) NOT NULL,
    capacity INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Create the users table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Create the bookings table
DROP TABLE IF EXISTS bookings;
CREATE TABLE bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    hotel_id BIGINT NOT NULL,
    check_in_date TIMESTAMP NOT NULL,  -- Changed to TIMESTAMP
    check_out_date TIMESTAMP NOT NULL, -- Changed to TIMESTAMP
    number_of_guests INT DEFAULT 1,
    total_price BIGINT,
    booking_status VARCHAR(10) DEFAULT "PENDING",
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (hotel_id) REFERENCES hotels(id)
);


-- Insert a large amount of data - Corrected INSERT query for TIMESTAMP
INSERT INTO hotels (name, city, address, capacity) VALUES
('Acme Hotel', 'New York', '123 Main St', 1),
('Beta Inn', 'Los Angeles', '456 Sunset Blvd', 2),
('Gamma Suites', 'Chicago', '789 Michigan Ave', 3),
('Delta Resort', 'Houston', '101 Allen Pkwy', 1),
('Epsilon Lodge', 'Phoenix', '202 Camelback Rd', 2),
('Zeta Grand Hotel', 'Philadelphia', '303 Market St', 2),
('Eta Plaza', 'San Antonio', '404 Riverwalk Dr', 1),
('Theta Inn', 'San Diego', '505 Broadway', 2),
('Iota Suites', 'Dallas', '606 Commerce St', 4),
('Kappa Resort', 'San Jose', '707 First St', 5),
('Lambda Lodge', 'Austin', '808 Congress Ave', 1),
('Mu Grand Hotel', 'Jacksonville', '909 Main St', 1),
('Nu Plaza', 'San Francisco', '101 California St', 2),
('Xi Inn', 'Columbus', '202 High St', 2),
('Omicron Suites', 'Charlotte', '303 Tryon St', 1),
('Pi Resort', 'Indianapolis', '404 Meridian St', 2),
('Rho Lodge', 'Seattle', '505 5th Ave', 1),
('Sigma Grand Hotel', 'Denver', '606 16th St', 1),
('Tau Plaza', 'Washington', '707 Pennsylvania Ave', 2),
('Upsilon Inn', 'Boston', '808 Boylston St', 2);

INSERT INTO users (username, password, email, first_name, last_name) VALUES
('john.doe', '$2a$10$properlyhashedpw1', 'john.doe@example.com', 'John', 'Doe'),
('jane.smith', '$2a$10$securehashedpass2', 'jane.smith@example.com', 'Jane', 'Smith'),
('peter.jones', '$2a$10$strongpasswordhash3', 'peter.jones@example.com', 'Peter', 'Jones'),
('mary.brown', '$2a$10$verysecurehash4', 'mary.brown@example.com', 'Mary', 'Brown'), 
('david.wilson', '$2a$10$complexhash5', 'david.wilson@example.com', 'David', 'Wilson'); 

INSERT INTO bookings (hotel_id, user_id, check_in_date, check_out_date, number_of_guests, total_price) VALUES
(1, 1, '2024-01-20 10:00:00', '2024-01-25 14:00:00', 2, 500),
(2, 2, '2024-02-10 12:00:00', '2024-02-14 11:00:00', 1, 300),
(3, 3, '2024-03-01 15:00:00', '2024-03-05 18:00:00', 3, 750),
(4, 4, '2024-04-15 09:00:00', '2024-04-20 10:00:00', 2, 600),
(5, 5, '2024-05-01 11:00:00', '2024-05-07 16:00:00', 4, 900),
(6, 1, '2024-06-10 14:00:00', '2024-06-15 12:00:00', 2, 550),
(7, 2, '2024-07-01 16:00:00', '2024-07-05 09:00:00', 1, 350),
(8, 3, '2024-08-15 18:00:00', '2024-08-20 15:00:00', 3, 800),
(9, 4, '2024-09-01 10:00:00', '2024-09-07 11:00:00', 2, 650),
(10, 5, '2024-10-10 12:00:00', '2024-10-15 14:00:00', 4, 950);