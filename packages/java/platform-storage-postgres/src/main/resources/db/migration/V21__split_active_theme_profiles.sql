-- Add migration SQL here.
ALTER TABLE theme_settings
    ADD COLUMN active_light_profile_id text,
    ADD COLUMN active_dark_profile_id text;

UPDATE theme_settings
SET active_light_profile_id = COALESCE(
        (SELECT id FROM theme_profiles WHERE id = 'varka-light'),
        active_profile_id
    ),
    active_dark_profile_id = COALESCE(active_profile_id, 'varka-dark');

ALTER TABLE theme_settings
    ALTER COLUMN active_light_profile_id SET NOT NULL,
    ALTER COLUMN active_dark_profile_id SET NOT NULL,
    ADD CONSTRAINT theme_settings_active_light_profile_fk
        FOREIGN KEY (active_light_profile_id) REFERENCES theme_profiles (id),
    ADD CONSTRAINT theme_settings_active_dark_profile_fk
        FOREIGN KEY (active_dark_profile_id) REFERENCES theme_profiles (id);

ALTER TABLE theme_settings
    DROP COLUMN active_profile_id;
