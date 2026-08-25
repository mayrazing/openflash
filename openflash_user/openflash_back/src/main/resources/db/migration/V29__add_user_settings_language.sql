ALTER TABLE pw_user_settings
  ADD COLUMN language VARCHAR(10) NOT NULL DEFAULT 'en'
  COMMENT '界面语言：zh/en/fi/de';
