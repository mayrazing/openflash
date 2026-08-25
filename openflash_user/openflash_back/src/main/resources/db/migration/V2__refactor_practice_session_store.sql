ALTER TABLE pw_practice_session_store
    RENAME TO pw_practice_session_store_legacy_v1;

CREATE TABLE pw_practice_session_store (
  user_id bigint NOT NULL,
  deck_id bigint NOT NULL,
  data longtext NOT NULL,
  updated_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, deck_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO pw_practice_session_store (user_id, deck_id, data, updated_at)
WITH ranked_sessions AS (
    SELECT user_id,
           deck_id,
           session_date,
           data,
           updated_at,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, deck_id
               ORDER BY updated_at DESC, session_date DESC
           ) AS rn
    FROM pw_practice_session_store_legacy_v1
    WHERE type = 'session'
),
ranked_orphan_aux AS (
    SELECT t.user_id,
           t.deck_id,
           t.session_date,
           MAX(t.updated_at) AS updated_at,
           ROW_NUMBER() OVER (
               PARTITION BY t.user_id, t.deck_id
               ORDER BY MAX(t.updated_at) DESC, t.session_date DESC
           ) AS rn
    FROM pw_practice_session_store_legacy_v1 t
    WHERE t.type IN ('retry_queue', 'post_round_retry')
      AND NOT EXISTS (
          SELECT 1
          FROM pw_practice_session_store_legacy_v1 s_any
          WHERE s_any.user_id = t.user_id
            AND s_any.deck_id = t.deck_id
            AND s_any.type = 'session'
      )
      AND NOT EXISTS (
          SELECT 1
          FROM pw_practice_session_store_legacy_v1 s
          WHERE s.user_id = t.user_id
            AND s.deck_id = t.deck_id
            AND s.session_date = t.session_date
            AND s.type = 'session'
      )
    GROUP BY t.user_id, t.deck_id, t.session_date
)
SELECT s.user_id,
       s.deck_id,
       CAST(
           JSON_SET(
               CAST(s.data AS JSON),
               '$.retryQueueItems',
               COALESCE(JSON_EXTRACT(r.data, '$.items'), JSON_ARRAY()),
               '$.postRoundRetryCards',
               COALESCE(JSON_EXTRACT(p.data, '$.cards'), JSON_ARRAY()),
               '$.history',
               JSON_ARRAY()
           ) AS CHAR CHARACTER SET utf8mb4
       ) AS data,
       GREATEST(
           s.updated_at,
           COALESCE(r.updated_at, s.updated_at),
           COALESCE(p.updated_at, s.updated_at)
       ) AS updated_at
FROM ranked_sessions s
LEFT JOIN pw_practice_session_store_legacy_v1 r
    ON r.user_id = s.user_id
   AND r.deck_id = s.deck_id
   AND r.session_date = s.session_date
   AND r.type = 'retry_queue'
LEFT JOIN pw_practice_session_store_legacy_v1 p
    ON p.user_id = s.user_id
   AND p.deck_id = s.deck_id
   AND p.session_date = s.session_date
   AND p.type = 'post_round_retry'
WHERE s.rn = 1

UNION ALL

SELECT a.user_id,
       a.deck_id,
       CAST(
           JSON_OBJECT(
               'mode',
               COALESCE(JSON_UNQUOTE(JSON_EXTRACT(r.data, '$.mode')), 'random'),
               'queueItems',
               COALESCE(JSON_EXTRACT(r.data, '$.items'), JSON_ARRAY()),
               'current',
               0,
               'revealed',
               FALSE,
               'practiceFinished',
               JSON_LENGTH(COALESCE(JSON_EXTRACT(p.data, '$.cards'), JSON_ARRAY())) > 0,
               'masteredQueue',
               JSON_ARRAY(),
               'postRoundRetryActive',
               FALSE,
               'retryQueueItems',
               COALESCE(JSON_EXTRACT(r.data, '$.items'), JSON_ARRAY()),
               'postRoundRetryCards',
               COALESCE(JSON_EXTRACT(p.data, '$.cards'), JSON_ARRAY()),
               'history',
               JSON_ARRAY(),
               'stats',
               JSON_OBJECT('again', 0, 'hard', 0, 'good', 0, 'easy', 0, 'newCount', 0, 'reviewCountStat', 0, 'masteredCount', 0),
               'firstRatedIds',
               JSON_ARRAY(),
               'cardProgressState',
               JSON_OBJECT('requiredDirectionsByCard', JSON_OBJECT(), 'completedDirectionsByCard', JSON_OBJECT()),
               'sessionSchemaVersion',
               2,
               'settingsNewCardsPerDay',
               10,
               'savedAt',
               UNIX_TIMESTAMP(a.updated_at) * 1000
           ) AS CHAR CHARACTER SET utf8mb4
       ) AS data,
       a.updated_at
FROM ranked_orphan_aux a
LEFT JOIN pw_practice_session_store_legacy_v1 r
    ON r.user_id = a.user_id
   AND r.deck_id = a.deck_id
   AND r.session_date = a.session_date
   AND r.type = 'retry_queue'
LEFT JOIN pw_practice_session_store_legacy_v1 p
    ON p.user_id = a.user_id
   AND p.deck_id = a.deck_id
   AND p.session_date = a.session_date
   AND p.type = 'post_round_retry'
WHERE a.rn = 1;
