CREATE TABLE users (
    id VARCHAR(128) PRIMARY KEY,
    invite_code VARCHAR(128),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE rooms (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(128),
    room_number VARCHAR(128),
    encrypted_info TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE black_list (
    user_id VARCHAR(128),
    blocked_user_id VARCHAR(128),
    created_at TIMESTAMP,
    PRIMARY KEY (user_id, blocked_user_id)
);

CREATE TABLE white_list (
    user_id VARCHAR(128),
    allowed_user_id VARCHAR(128),
    created_at TIMESTAMP,
    PRIMARY KEY (user_id, allowed_user_id)
);
