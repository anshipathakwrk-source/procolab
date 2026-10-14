-- =====================================================================
--  Project Collaboration Platform  -  MySQL 8 schema
--  Run:  mysql -u root -p < database/schema.sql
--  (Optional: Spring Boot can also create these tables for you when
--   spring.jpa.hibernate.ddl-auto=update. If you run this script instead,
--   set that property to 'none'.)
-- =====================================================================
CREATE DATABASE IF NOT EXISTS project_collab
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE project_collab;

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(100) NOT NULL,
  email         VARCHAR(150) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS projects (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  title       VARCHAR(150) NOT NULL,
  description TEXT,
  owner_id    BIGINT       NOT NULL,
  start_date  DATE,
  end_date    DATE,
  status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | ARCHIVED
  created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_projects_owner FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS project_members (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  role       VARCHAR(20) NOT NULL DEFAULT 'MEMBER',     -- OWNER | MANAGER | MEMBER
  UNIQUE KEY uq_member (project_id, user_id),
  CONSTRAINT fk_pm_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
  CONSTRAINT fk_pm_user    FOREIGN KEY (user_id)    REFERENCES users(id)    ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tasks (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id  BIGINT       NOT NULL,
  title       VARCHAR(200) NOT NULL,
  description TEXT,
  assignee_id BIGINT       NULL,
  created_by  BIGINT       NOT NULL,
  priority    VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM',   -- LOW | MEDIUM | HIGH
  status      VARCHAR(20)  NOT NULL DEFAULT 'TODO',     -- TODO | IN_PROGRESS | DONE
  due_date    DATE,
  created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_tasks_project_status (project_id, status),
  INDEX idx_tasks_assignee (assignee_id),
  CONSTRAINT fk_tasks_project  FOREIGN KEY (project_id)  REFERENCES projects(id) ON DELETE CASCADE,
  CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users(id)    ON DELETE SET NULL,
  CONSTRAINT fk_tasks_creator  FOREIGN KEY (created_by)  REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS comments (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id    BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  parent_id  BIGINT      NULL,                          -- NULL = top-level, else a reply
  content    TEXT        NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_comments_task (task_id),
  CONSTRAINT fk_comments_task   FOREIGN KEY (task_id)   REFERENCES tasks(id)    ON DELETE CASCADE,
  CONSTRAINT fk_comments_user   FOREIGN KEY (user_id)   REFERENCES users(id),
  CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS files (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id   BIGINT       NOT NULL,
  task_id      BIGINT       NULL,
  uploaded_by  BIGINT       NOT NULL,
  file_name    VARCHAR(255) NOT NULL,                   -- name shown to users
  stored_name  VARCHAR(300) NOT NULL,                   -- unique name on disk
  content_type VARCHAR(150),
  size_bytes   BIGINT       NOT NULL,
  uploaded_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_files_project (project_id),
  CONSTRAINT fk_files_project FOREIGN KEY (project_id)  REFERENCES projects(id) ON DELETE CASCADE,
  CONSTRAINT fk_files_task    FOREIGN KEY (task_id)     REFERENCES tasks(id)    ON DELETE SET NULL,
  CONSTRAINT fk_files_user    FOREIGN KEY (uploaded_by) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS activity_log (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  action     VARCHAR(300) NOT NULL,
  created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  INDEX idx_activity_project (project_id, created_at),
  CONSTRAINT fk_activity_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
  CONSTRAINT fk_activity_user    FOREIGN KEY (user_id)    REFERENCES users(id)
) ENGINE=InnoDB;
