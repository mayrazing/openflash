-- 把 mask-mode 类型注册行从中文展示文案修正为 i18n key，DB 只存语言无关数据。
UPDATE `pw_type_registry`
SET `item_name` = 'plugins.mask-mode.name',
    `config`    = '{"descKey":"plugins.mask-mode.desc","icon":"🙈","categoryKey":"pluginCategories.studyAid"}'
WHERE `registry_type` = 'plugin'
  AND `item_key`      = 'mask-mode';
