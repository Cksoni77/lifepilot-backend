CREATE TABLE task_activity_details (
    task_id UUID PRIMARY KEY REFERENCES tasks(id) ON DELETE CASCADE,
    profile VARCHAR(30) NOT NULL,
    activity_type VARCHAR(40) NOT NULL,
    fields JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT chk_task_activity_details_fields_object
        CHECK (jsonb_typeof(fields) = 'object')
);
