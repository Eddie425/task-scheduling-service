CREATE TABLE IF NOT EXISTS scheduled_task (
    task_id VARCHAR(128) NOT NULL PRIMARY KEY,
    execute_at TIMESTAMP(6) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_task_due (status, execute_at, task_id),
    CONSTRAINT chk_task_status CHECK (status IN ('PENDING', 'DISPATCHING', 'TRIGGERED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS task_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(128) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_error VARCHAR(1000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_outbox_task (task_id),
    INDEX idx_outbox_dispatch (status, next_attempt_at, id),
    CONSTRAINT fk_outbox_task FOREIGN KEY (task_id) REFERENCES scheduled_task (task_id),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'SENT')),
    CONSTRAINT chk_outbox_attempt_count CHECK (attempt_count >= 0)
);
