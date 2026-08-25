UPDATE pw_user_ai_config
SET provider = 'deepseek',
    config = JSON_OBJECT(
        'protocol', 'ANTHROPIC',
        'displayName', 'DeepSeek',
        'website', 'https://www.deepseek.com',
        'note', '',
        'baseUrl', 'https://api.deepseek.com/anthropic',
        'apiKeyEnc', JSON_UNQUOTE(JSON_EXTRACT(config, '$.apiKeyEnc')),
        'model', JSON_UNQUOTE(JSON_EXTRACT(config, '$.model'))
    )
WHERE provider = 'DEEPSEEK';

UPDATE pw_user_ai_config
SET provider = 'ollama',
    config = JSON_OBJECT(
        'protocol', 'ANTHROPIC',
        'displayName', 'Ollama',
        'website', 'https://ollama.com',
        'note', '',
        'baseUrl', COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(config, '$.baseUrl')), ''), 'http://localhost:11434'),
        'model', COALESCE(NULLIF(JSON_UNQUOTE(JSON_EXTRACT(config, '$.model')), ''), 'qwen3.5:9b')
    )
WHERE provider = 'OLLAMA';
