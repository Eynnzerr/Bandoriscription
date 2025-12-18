CREATE TABLE chat_groups (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_activity_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE chat_group_members (
    group_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    username VARCHAR(255) NOT NULL DEFAULT '',
    avatar TEXT NOT NULL DEFAULT '',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, user_id),
    UNIQUE (user_id),
    FOREIGN KEY (group_id) REFERENCES chat_groups(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE chat_messages (
    id BIGSERIAL PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    username VARCHAR(255) NOT NULL DEFAULT '',
    avatar TEXT NOT NULL DEFAULT '',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (group_id) REFERENCES chat_groups(id) ON DELETE CASCADE
);
