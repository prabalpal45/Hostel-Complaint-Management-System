-- Hostel Complaint & Maintenance Management System
-- Database Schema for MySQL 8.0+

CREATE DATABASE IF NOT EXISTS hostel_complaint_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE hostel_complaint_db;

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(30) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    room_number VARCHAR(20) DEFAULT NULL,
    department VARCHAR(100) DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    phone VARCHAR(20) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Complaints Table
CREATE TABLE IF NOT EXISTS complaints (
    complaint_id VARCHAR(30) PRIMARY KEY,
    student_id VARCHAR(30) NOT NULL,
    room_number VARCHAR(20),
    category VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    assigned_staff_id VARCHAR(30) DEFAULT NULL,
    remarks TEXT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_complaints_student
        FOREIGN KEY (student_id) REFERENCES users (user_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_complaints_staff
        FOREIGN KEY (assigned_staff_id) REFERENCES users (user_id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Complaint History Table (Audit Trail)
CREATE TABLE IF NOT EXISTS complaint_history (
    history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    complaint_id VARCHAR(30) NOT NULL,
    old_status VARCHAR(30),
    new_status VARCHAR(30),
    updated_by VARCHAR(30),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_complaint
        FOREIGN KEY (complaint_id) REFERENCES complaints (complaint_id)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_history_updated_by
        FOREIGN KEY (updated_by) REFERENCES users (user_id)
        ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for performance
CREATE INDEX idx_complaints_student ON complaints(student_id);
CREATE INDEX idx_complaints_staff ON complaints(assigned_staff_id);
CREATE INDEX idx_complaints_status ON complaints(status);
CREATE INDEX idx_history_complaint ON complaint_history(complaint_id);
