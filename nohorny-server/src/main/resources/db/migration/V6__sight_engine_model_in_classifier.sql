-- Names the SightEngine model in the classifier of the requests and their steps, like the new ones. The requests
-- recorded before were all rated by nudity-2.1, the only model SightEngine was called with until then.

UPDATE request SET classifier = 'sight-engine/nudity-2.1' WHERE classifier = 'sight-engine';
UPDATE request_step SET classifier = 'sight-engine/nudity-2.1' WHERE classifier = 'sight-engine';
