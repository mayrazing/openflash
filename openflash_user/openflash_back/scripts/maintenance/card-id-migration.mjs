function normalizeMapping(mapping) {
  return new Map([...mapping].map(([oldId, newId]) => [String(oldId), Number(newId)]))
}

function remapScalarId(value, mapping, strict) {
  const mapped = mapping.get(String(value))
  if (mapped === undefined) {
    if (strict && value !== null && value !== undefined) {
      throw new Error(`unmapped card id ${value} in persisted practice session`)
    }
    return value
  }
  return typeof value === 'string' ? String(mapped) : mapped
}

function remapStructuredIdentity(value, mapping, strict) {
  if (typeof value !== 'string') return value
  const match = value.match(/^(\d+)(:.*)$/)
  if (!match) {
    if (strict) throw new Error(`invalid persisted card identity ${value}`)
    return value
  }
  const mapped = mapping.get(match[1])
  if (mapped === undefined) {
    if (strict) throw new Error(`unmapped card id ${match[1]} in persisted practice session`)
    return value
  }
  return `${mapped}${match[2]}`
}

const IDENTITY_VALUE_PROPERTIES = new Set(['itemKey', 'sourceItemKey', 'firstRatedIds'])
const CARD_ID_KEYED_PROPERTIES = new Set(['requiredDirectionsByCard', 'completedDirectionsByCard'])

function remapObjectKey(key, mapping, propertyName, strict) {
  if (!CARD_ID_KEYED_PROPERTIES.has(propertyName)) return key
  if (/^\d+$/.test(key)) return String(remapScalarId(key, mapping, strict))
  return remapStructuredIdentity(key, mapping, strict)
}

function isPersistedCardSnapshot(value) {
  return value !== null
    && typeof value === 'object'
    && !Array.isArray(value)
    && Object.prototype.hasOwnProperty.call(value, 'id')
    && Object.prototype.hasOwnProperty.call(value, 'deckId')
}

function remapValue(value, mapping, propertyName = null, strict = false) {
  if (Array.isArray(value)) {
    return value.map(item => remapValue(item, mapping, propertyName, strict))
  }
  if (value === null || typeof value !== 'object') {
    if (propertyName === 'cardId') return remapScalarId(value, mapping, strict)
    return IDENTITY_VALUE_PROPERTIES.has(propertyName)
      ? remapStructuredIdentity(value, mapping, strict)
      : value
  }

  const cardSnapshot = isPersistedCardSnapshot(value)
  const result = {}
  for (const [key, child] of Object.entries(value)) {
    const nextKey = remapObjectKey(key, mapping, propertyName, strict)
    if (Object.prototype.hasOwnProperty.call(result, nextKey)) {
      throw new Error(`card-id remap produced duplicate practice-session key: ${nextKey}`)
    }
    if (key === 'cardId' || (key === 'id' && cardSnapshot)) {
      result[nextKey] = remapScalarId(child, mapping, strict)
    } else {
      result[nextKey] = remapValue(child, mapping, key, strict)
    }
  }
  return result
}

/** 重写持久化练习会话中的卡片编号,不修改卡片内容和 FSRS 数值。 */
export function remapPracticeSessionCardIds(session, mapping, options = {}) {
  if (!(mapping instanceof Map)) {
    throw new TypeError('mapping must be a Map')
  }
  return remapValue(session, normalizeMapping(mapping), null, Boolean(options.strict))
}

function sqlInteger(value, label) {
  const text = String(value)
  if (!/^\d+$/.test(text)) throw new TypeError(`${label} must be a positive integer`)
  return text
}

function sqlTimestamp(value, label) {
  const text = String(value)
  if (!/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(?:\.\d{1,6})?$/.test(text)) {
    throw new TypeError(`${label} must be a PostgreSQL timestamp`)
  }
  return `'${text}'`
}

function sqlUtf8(value) {
  return `convert_from(decode('${Buffer.from(value, 'utf8').toString('hex')}', 'hex'), 'UTF8')`
}

function sqlFingerprint(value, label) {
  const text = String(value)
  if (!/^[a-f0-9]{32}$/.test(text)) throw new TypeError(`${label} must be an md5 fingerprint`)
  return `'${text}'`
}

function commaSeparatedIntegers(values, label) {
  if (!Array.isArray(values) || values.length === 0) throw new TypeError(`${label} must not be empty`)
  return values.map(value => sqlInteger(value, label)).join(', ')
}

function preservedPairPredicate(pairs, userColumn = 'user_id', deckColumn = 'deck_id') {
  if (!Array.isArray(pairs) || pairs.length === 0) throw new TypeError('keepPairs must not be empty')
  return pairs.map(pair => (
    `(${userColumn} = ${sqlInteger(pair.userId, 'userId')} AND ${deckColumn} = ${sqlInteger(pair.deckId, 'deckId')})`
  )).join(' OR ')
}

/** 生成只保留指定账号和卡包的卡片编号迁移 SQL。 */
export function buildCardIdMigrationSql({ cards, sessions, keepUsers, keepDecks, keepPairs, scopes, schema }) {
  if (!Array.isArray(cards) || cards.length === 0) throw new TypeError('cards must not be empty')
  if (!Array.isArray(scopes) || scopes.length === 0) throw new TypeError('scopes must not be empty')
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(schema)) {
    throw new TypeError('schema must be a simple PostgreSQL identifier')
  }

  const mappingRows = cards.map((card, index) => {
    const expectedNewId = index + 1
    if (Number(card.newId) !== expectedNewId) {
      throw new Error(`card new ids must be contiguous from 1; expected ${expectedNewId}`)
    }
    const newId = sqlInteger(card.newId, 'newId')
    const tempId = `-${newId}`
    return `(${sqlInteger(card.oldId, 'oldId')}, ${tempId}, ${newId}, ${sqlInteger(card.deckId, 'deckId')}, ${sqlInteger(card.userId, 'userId')}, ${sqlTimestamp(card.updatedAt, 'card updatedAt')}, ${sqlFingerprint(card.cardFingerprint, 'card fingerprint')})`
  })

  const userIds = commaSeparatedIntegers(keepUsers, 'keepUsers')
  const deckIds = commaSeparatedIntegers(keepDecks, 'keepDecks')
  const pairPredicate = preservedPairPredicate(keepPairs)
  const cardScopePredicate = preservedPairPredicate(keepPairs, 'd.user_id', 'c.deck_id')
  const sessionScopePredicate = preservedPairPredicate(keepPairs, 's.user_id', 's.deck_id')
  const scopeSnapshotRows = scopes.map(scope => (
    `(${sqlInteger(scope.userId, 'scope userId')}, ${sqlUtf8(scope.username)}, ${sqlInteger(scope.userDeleted, 'scope userDeleted')}, ${sqlTimestamp(scope.userUpdatedAt, 'scope user updatedAt')}, ${sqlInteger(scope.deckId, 'scope deckId')}, ${sqlUtf8(scope.deckName)}, ${sqlInteger(scope.deckDeleted, 'scope deckDeleted')}, ${sqlTimestamp(scope.deckUpdatedAt, 'scope deck updatedAt')})`
  ))
  const sessionSnapshotRows = (sessions ?? []).map(session => {
    JSON.parse(session.data)
    JSON.parse(session.sourceData)
    return `(${sqlInteger(session.userId, 'session userId')}, ${sqlInteger(session.deckId, 'session deckId')}, ${sqlUtf8(session.sourceData)}, ${sqlUtf8(session.data)}, ${sqlTimestamp(session.updatedAt, 'session updatedAt')})`
  })

  const sql = [
    'START TRANSACTION;',
    'LOCK TABLE',
    '    pw_user, pw_deck, pw_card, pw_card_progress, pw_card_media,',
    '    pw_deck_ai_settings, pw_deck_settings, pw_mask_mode_deck_settings,',
    '    pw_tts_deck_settings, pw_plugin_install, pw_practice_session_store,',
    '    pw_user_ai_config, pw_user_settings, pw_async_task,',
    '    pw_platform_ai_user_access, pw_card_ai_cache, pw_user_active_ai_selection,',
    '    pw_user_feature_flag, pw_user_platform_ai_preference, pw_user_upload,',
    '    SPRING_SESSION_ATTRIBUTES, SPRING_SESSION',
    'IN ACCESS EXCLUSIVE MODE;',
    'CREATE TEMPORARY TABLE card_id_migration_scope_snapshot (',
    '    user_id BIGINT NOT NULL,',
    '    username TEXT NOT NULL,',
    '    user_deleted SMALLINT NOT NULL,',
    '    user_updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,',
    '    deck_id BIGINT NOT NULL,',
    '    deck_name TEXT NOT NULL,',
    '    deck_deleted SMALLINT NOT NULL,',
    '    deck_updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,',
    '    PRIMARY KEY (user_id, deck_id)',
    ') ON COMMIT DROP;',
    `INSERT INTO card_id_migration_scope_snapshot (user_id, username, user_deleted, user_updated_at, deck_id, deck_name, deck_deleted, deck_updated_at) VALUES\n${scopeSnapshotRows.join(',\n')};`,
    'CREATE TEMPORARY TABLE card_id_migration_map (',
    '    old_id INT NOT NULL PRIMARY KEY,',
    '    temp_id INT NOT NULL UNIQUE,',
    '    new_id INT NOT NULL UNIQUE,',
    '    deck_id BIGINT NOT NULL,',
    '    user_id BIGINT NOT NULL,',
    '    card_updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,',
    '    card_fingerprint CHAR(32) NOT NULL',
    ') ON COMMIT DROP;',
    `INSERT INTO card_id_migration_map (old_id, temp_id, new_id, deck_id, user_id, card_updated_at, card_fingerprint) VALUES\n${mappingRows.join(',\n')};`,
    'CREATE TEMPORARY TABLE card_id_migration_session_snapshot (',
    '    user_id BIGINT NOT NULL,',
    '    deck_id BIGINT NOT NULL,',
    '    source_data TEXT NOT NULL,',
    '    remapped_data TEXT NOT NULL,',
    '    session_updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,',
    '    PRIMARY KEY (user_id, deck_id)',
    ') ON COMMIT DROP;',
    ...(sessionSnapshotRows.length === 0 ? [] : [
      `INSERT INTO card_id_migration_session_snapshot (user_id, deck_id, source_data, remapped_data, session_updated_at) VALUES\n${sessionSnapshotRows.join(',\n')};`,
    ]),
    'CREATE TEMPORARY TABLE card_id_migration_progress_time AS',
    'SELECT p.id, p.updated_at',
    'FROM pw_card_progress AS p',
    'INNER JOIN card_id_migration_map AS m ON m.old_id = p.card_id;',
    'DO $$',
    'BEGIN',
    '    IF EXISTS (',
    '           SELECT 1 FROM pg_trigger',
    "           WHERE tgrelid IN ('pw_user'::regclass, 'pw_deck'::regclass,",
    "                             'pw_card'::regclass, 'pw_card_progress'::regclass,",
    "                             'pw_card_media'::regclass, 'pw_practice_session_store'::regclass)",
    "             AND tgenabled <> 'O'",
    '       ) THEN',
    "        RAISE EXCEPTION 'card-id migration requires all affected triggers to be normally enabled';",
    '    END IF;',
    '    IF EXISTS (',
    '           SELECT 1',
    '           FROM card_id_migration_scope_snapshot AS snapshot',
    '           LEFT JOIN pw_user AS u ON u.id = snapshot.user_id',
    '           LEFT JOIN pw_deck AS d ON d.id = snapshot.deck_id',
    '           WHERE u.id IS NULL OR d.id IS NULL OR d.user_id <> snapshot.user_id',
    '              OR u.username IS DISTINCT FROM snapshot.username',
    '              OR u.deleted IS DISTINCT FROM snapshot.user_deleted',
    '              OR u.updated_at IS DISTINCT FROM snapshot.user_updated_at',
    '              OR d.name IS DISTINCT FROM snapshot.deck_name',
    '              OR d.deleted IS DISTINCT FROM snapshot.deck_deleted',
    '              OR d.updated_at IS DISTINCT FROM snapshot.deck_updated_at',
    '       ) OR (',
    '           SELECT COUNT(*)',
    '           FROM pw_user AS u',
    '           INNER JOIN pw_deck AS d ON d.user_id = u.id',
    '           INNER JOIN card_id_migration_scope_snapshot AS snapshot',
    '             ON snapshot.username = u.username AND snapshot.deck_name = d.name',
    '           WHERE u.deleted = 0 AND d.deleted = 0',
    '       ) <> (SELECT COUNT(*) FROM card_id_migration_scope_snapshot) THEN',
    "        RAISE EXCEPTION 'card scope changed after migration snapshot';",
    '    END IF;',
    `    IF (SELECT COUNT(*) FROM pw_card AS c INNER JOIN pw_deck AS d ON d.id = c.deck_id WHERE c.deleted = 0 AND (${cardScopePredicate})) <> (SELECT COUNT(*) FROM card_id_migration_map)`,
    '       OR EXISTS (',
    '           SELECT 1',
    '           FROM card_id_migration_map AS m',
    '           LEFT JOIN pw_card AS c ON c.id = m.old_id',
    '           LEFT JOIN pw_deck AS d ON d.id = c.deck_id',
    '           WHERE c.id IS NULL OR c.deleted <> 0 OR c.deck_id <> m.deck_id',
    '              OR d.user_id <> m.user_id OR c.updated_at IS DISTINCT FROM m.card_updated_at',
    '              OR md5(row_to_json(c)::text) IS DISTINCT FROM m.card_fingerprint',
    '       ) THEN',
    "        RAISE EXCEPTION 'card data changed after migration snapshot';",
    '    END IF;',
    `    IF (SELECT COUNT(*) FROM pw_practice_session_store AS s WHERE ${sessionScopePredicate}) <> (SELECT COUNT(*) FROM card_id_migration_session_snapshot)`,
    '       OR EXISTS (',
    '           SELECT 1',
    '           FROM card_id_migration_session_snapshot AS snapshot',
    '           LEFT JOIN pw_practice_session_store AS s',
    '             ON s.user_id = snapshot.user_id AND s.deck_id = snapshot.deck_id',
    '           WHERE s.user_id IS NULL OR s.data IS DISTINCT FROM snapshot.source_data',
    '              OR s.updated_at IS DISTINCT FROM snapshot.session_updated_at',
    '       ) THEN',
    "        RAISE EXCEPTION 'practice session changed after migration snapshot';",
    '    END IF;',
    'END',
    '$$;',
    'ALTER TABLE pw_card DISABLE TRIGGER ALL;',
    'ALTER TABLE pw_card_progress DISABLE TRIGGER ALL;',
    'ALTER TABLE pw_card_media DISABLE TRIGGER ALL;',
    'ALTER TABLE pw_practice_session_store DISABLE TRIGGER USER;',
    'DELETE FROM pw_card_progress AS p',
    'WHERE NOT EXISTS (',
    '    SELECT 1 FROM card_id_migration_map AS m',
    '    WHERE m.old_id = p.card_id AND m.user_id = p.user_id',
    ');',
    'DELETE FROM pw_card_media AS cm',
    'WHERE NOT EXISTS (',
    '    SELECT 1 FROM card_id_migration_map AS m WHERE m.old_id = cm.card_id',
    ');',
    'DELETE FROM pw_card AS c',
    'WHERE NOT EXISTS (',
    '    SELECT 1 FROM card_id_migration_map AS m WHERE m.old_id = c.id',
    ');',
    `DELETE FROM pw_deck_ai_settings WHERE deck_id NOT IN (${deckIds});`,
    `DELETE FROM pw_deck_settings WHERE deck_id NOT IN (${deckIds});`,
    `DELETE FROM pw_mask_mode_deck_settings WHERE deck_id NOT IN (${deckIds});`,
    `DELETE FROM pw_tts_deck_settings WHERE deck_id NOT IN (${deckIds});`,
    `DELETE FROM pw_plugin_install WHERE NOT (${pairPredicate});`,
    `DELETE FROM pw_practice_session_store WHERE NOT (${pairPredicate});`,
    `DELETE FROM pw_user_ai_config WHERE user_id NOT IN (${userIds});`,
    `DELETE FROM pw_user_settings WHERE user_id NOT IN (${userIds});`,
    'DELETE FROM pw_async_task;',
    'DELETE FROM SPRING_SESSION_ATTRIBUTES;',
    'DELETE FROM SPRING_SESSION;',
    'UPDATE pw_card_progress AS p',
    'SET card_id = m.temp_id',
    'FROM card_id_migration_map AS m',
    'WHERE m.old_id = p.card_id;',
    'UPDATE pw_card_media AS cm',
    'SET card_id = m.temp_id',
    'FROM card_id_migration_map AS m',
    'WHERE m.old_id = cm.card_id;',
    'UPDATE pw_card AS c',
    'SET id = m.temp_id, updated_at = m.card_updated_at',
    'FROM card_id_migration_map AS m',
    'WHERE m.old_id = c.id;',
    'UPDATE pw_card AS c',
    'SET id = m.new_id, updated_at = m.card_updated_at',
    'FROM card_id_migration_map AS m',
    'WHERE m.temp_id = c.id;',
    'UPDATE pw_card_progress AS p',
    'SET card_id = m.new_id, updated_at = t.updated_at',
    'FROM card_id_migration_map AS m, card_id_migration_progress_time AS t',
    'WHERE m.temp_id = p.card_id AND t.id = p.id;',
    'UPDATE pw_card_media AS cm',
    'SET card_id = m.new_id',
    'FROM card_id_migration_map AS m',
    'WHERE m.temp_id = cm.card_id;',
    'UPDATE pw_practice_session_store AS s',
    'SET data = snapshot.remapped_data, updated_at = snapshot.session_updated_at',
    'FROM card_id_migration_session_snapshot AS snapshot',
    'WHERE snapshot.user_id = s.user_id AND snapshot.deck_id = s.deck_id;',
    `DELETE FROM pw_deck WHERE id NOT IN (${deckIds});`,
    `DELETE FROM pw_user WHERE id NOT IN (${userIds});`,
    'DO $$',
    'BEGIN',
    '    IF EXISTS (SELECT 1 FROM pw_card_progress AS p LEFT JOIN pw_card AS c ON c.id = p.card_id WHERE c.id IS NULL)',
    '       OR EXISTS (SELECT 1 FROM pw_card_media AS cm LEFT JOIN pw_card AS c ON c.id = cm.card_id WHERE c.id IS NULL) THEN',
    "        RAISE EXCEPTION 'card-id migration produced orphan card references';",
    '    END IF;',
    'END',
    '$$;',
    'ALTER TABLE pw_practice_session_store ENABLE TRIGGER USER;',
    'ALTER TABLE pw_card_media ENABLE TRIGGER ALL;',
    'ALTER TABLE pw_card_progress ENABLE TRIGGER ALL;',
    'ALTER TABLE pw_card ENABLE TRIGGER ALL;',
    `ALTER SEQUENCE pw_card_id_seq RESTART WITH ${cards.length + 1};`,
    'COMMIT;',
  ]
  const persistentRelations = [
    'pw_platform_ai_user_access',
    'pw_user_platform_ai_preference',
    'pw_user_active_ai_selection',
    'pw_practice_session_store',
    'pw_mask_mode_deck_settings',
    'SPRING_SESSION_ATTRIBUTES',
    'pw_deck_ai_settings',
    'pw_tts_deck_settings',
    'pw_user_feature_flag',
    'pw_user_ai_config',
    'pw_card_progress',
    'pw_card_ai_cache',
    'pw_plugin_install',
    'pw_user_settings',
    'pw_deck_settings',
    'pw_card_id_seq',
    'pw_card_media',
    'pw_user_upload',
    'pw_async_task',
    'SPRING_SESSION',
    'pw_card',
    'pw_deck',
    'pw_user',
  ]
  const relationPattern = new RegExp(`\\b(${persistentRelations.join('|')})\\b`, 'g')
  return sql.join('\n').replaceAll(relationPattern, `${schema}.$1`)
}
