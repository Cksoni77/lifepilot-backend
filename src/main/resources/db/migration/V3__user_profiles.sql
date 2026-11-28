ALTER TABLE user_preferences
    ADD COLUMN profiles JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE user_preferences
    ADD CONSTRAINT chk_user_preferences_profiles_array
    CHECK (jsonb_typeof(profiles) = 'array');
