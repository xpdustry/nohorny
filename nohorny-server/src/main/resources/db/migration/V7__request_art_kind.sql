-- The kind of buildings the image was made with, sent by the plugin since it exists. NULL for the older clients.
ALTER TABLE request
    ADD COLUMN art_kind TEXT CHECK (art_kind IN ('LOGIC_DISPLAY', 'CANVAS', 'SORTER', 'ILLUMINATOR'));
