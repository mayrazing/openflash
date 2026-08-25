-- 移除 Ollama provider: 不再支持 Anthropic 协议下的 Ollama,删除全部 ollama 行,
-- 若有用户当前 active 是 ollama,先将该用户其它 provider 行任选一行置 active,避免该用户失去 active 配置。

-- Step 1: 把 active=ollama 用户的 active 切换到同用户最早的其它 provider 行。
UPDATE pw_user_ai_config target
JOIN (
    SELECT MIN(other.id) AS pick_id
    FROM pw_user_ai_config other
    JOIN (
        SELECT user_id
        FROM pw_user_ai_config
        WHERE provider = 'ollama'
          AND is_active = 1
    ) ollama_active ON other.user_id = ollama_active.user_id
    WHERE other.provider <> 'ollama'
    GROUP BY other.user_id
) replacement ON target.id = replacement.pick_id
SET target.is_active = 1;

-- Step 2: 删除所有 ollama 行。
DELETE FROM pw_user_ai_config WHERE provider = 'ollama';
