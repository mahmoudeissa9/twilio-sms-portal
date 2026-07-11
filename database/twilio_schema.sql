-- Create ENUM
CREATE TYPE user_role AS ENUM ('ADMIN', 'CUSTOMER');

-- Users table
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(100) UNIQUE,
    password VARCHAR(255),
    role user_role DEFAULT 'CUSTOMER',
    phone_number_msisdn VARCHAR(20),
    birthday DATE,
    job VARCHAR(100),
    address VARCHAR(255),
    twilio_sid VARCHAR(100),
    twilio_token VARCHAR(100),
    allowed_sender_id VARCHAR(50),
    is_verified BOOLEAN DEFAULT FALSE
);

-- SMS table
CREATE TABLE sms_messages (
    message_id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    sender_from VARCHAR(50),
    receiver_to VARCHAR(50),
    body TEXT,
    message_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    direction VARCHAR(10) DEFAULT 'OUTBOUND'
);

-- Verification table
CREATE TABLE verification (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE CASCADE,
    code VARCHAR(10),
    expires_at TIMESTAMP,
    is_used BOOLEAN DEFAULT FALSE
);
