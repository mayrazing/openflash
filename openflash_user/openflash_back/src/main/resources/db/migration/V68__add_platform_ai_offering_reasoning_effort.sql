ALTER TABLE `pw_platform_ai_offering`
  ADD COLUMN `reasoning_effort` varchar(32) DEFAULT NULL AFTER `model_key`;
