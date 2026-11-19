ALTER TABLE tasks ADD COLUMN client_id VARCHAR(80);
ALTER TABLE habits ADD COLUMN client_id VARCHAR(80);
ALTER TABLE goals ADD COLUMN client_id VARCHAR(80);

CREATE UNIQUE INDEX uq_tasks_user_client_id
    ON tasks(user_id, client_id) WHERE client_id IS NOT NULL;
CREATE UNIQUE INDEX uq_habits_user_client_id
    ON habits(user_id, client_id) WHERE client_id IS NOT NULL;
CREATE UNIQUE INDEX uq_goals_user_client_id
    ON goals(user_id, client_id) WHERE client_id IS NOT NULL;
