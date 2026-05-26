CREATE TABLE `training_sets` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `title` VARCHAR(255) NOT NULL COMMENT '题单标题',
  `description` TEXT COMMENT '题单介绍/描述 (支持 Markdown)',
  `visible` TINYINT(1) DEFAULT 1 COMMENT '是否公开可见 (0:隐藏, 1:公开)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `training_problem_relation` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `training_id` BIGINT NOT NULL COMMENT '题单 ID',
  `problem_id` BIGINT NOT NULL COMMENT '题目 ID',
  `display_order` INT DEFAULT 0 COMMENT '显示顺序',
  UNIQUE KEY `uk_training_problem` (`training_id`, `problem_id`),
  INDEX `idx_training` (`training_id`),
  INDEX `idx_problem` (`problem_id`),
  CONSTRAINT `fk_tpr_training` FOREIGN KEY (`training_id`) REFERENCES `training_sets` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_tpr_problem` FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
