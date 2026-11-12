-- =====================================================================
-- Interval - Skema database (MySQL 8.0.16+)
--
-- Aturan umum:
--  * Semua kolom DATETIME disimpan dalam UTC. Konversi ke timezone
--    pengguna dilakukan di aplikasi (Entity pakai Instant,
--    hibernate.jdbc.time_zone=UTC).
--  * Collation utf8mb4_unicode_ci bersifat case-insensitive, jadi
--    UNIQUE email tidak membedakan huruf besar/kecil.
--  * Constraint CHECK baru ditegakkan mulai MySQL 8.0.16.
--  * Riwayat konsumsi (medication_logs) TIDAK ikut terhapus saat obat
--    atau jadwal dihapus; log menyimpan snapshot nama obat dan dosis.
--    Hanya penghapusan akun (users) yang menghapus log lewat user_id.
-- =====================================================================


-- =========================================
-- 1. TABLE: users
-- =========================================
CREATE TABLE users (
    id            CHAR(36)     PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    timezone      VARCHAR(50)  NOT NULL DEFAULT 'Asia/Jakarta',
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                               ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================
-- 2. TABLE: medications
-- is_active  = obat dijeda/aktif (pengingat berhenti jika false)
-- deleted_at = soft delete (NULL = belum dihapus)
-- =========================================
CREATE TABLE medications (
    id           CHAR(36)     PRIMARY KEY,
    user_id      CHAR(36)     NOT NULL,
    name         VARCHAR(150) NOT NULL,
    dosage       VARCHAR(100),
    dosage_form  VARCHAR(20)  NOT NULL DEFAULT 'OTHER',
    instructions TEXT,
    notes        TEXT,
    image_url    VARCHAR(500),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at   DATETIME(3),
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                              ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_medications_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    CONSTRAINT chk_medications_dosage_form
        CHECK (dosage_form IN ('TABLET','CAPSULE','SYRUP','DROPS','OTHER')),

    INDEX idx_medications_user (user_id, deleted_at, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================
-- 3. TABLE: medication_schedules
-- Aturan jadwal. Jam dipahami dalam timezone pengguna (users.timezone),
-- sehingga tidak ada kolom timezone di tabel ini.
-- Untuk frequency_type = INTERVAL, start_date menjadi tanggal acuan
-- penghitungan setiap interval_days hari.
-- =========================================
CREATE TABLE medication_schedules (
    id             CHAR(36)     PRIMARY KEY,
    medication_id  CHAR(36)     NOT NULL,
    frequency_type VARCHAR(20)  NOT NULL,
    interval_days  INT,
    dose_quantity  DECIMAL(8,2) NOT NULL,
    dose_unit      VARCHAR(20)  NOT NULL DEFAULT 'TABLET',
    start_date     DATE         NOT NULL,
    end_date       DATE,
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted_at     DATETIME(3),
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_schedules_medication
        FOREIGN KEY (medication_id) REFERENCES medications(id) ON DELETE CASCADE,

    CONSTRAINT chk_schedules_frequency
        CHECK (frequency_type IN ('DAILY','WEEKLY','INTERVAL')),
    CONSTRAINT chk_schedules_interval
        CHECK (frequency_type <> 'INTERVAL'
               OR (interval_days IS NOT NULL AND interval_days >= 1)),
    CONSTRAINT chk_schedules_dose_quantity
        CHECK (dose_quantity > 0),
    CONSTRAINT chk_schedules_dose_unit
        CHECK (dose_unit IN ('TABLET','CAPSULE','ML','DROP','OTHER')),
    CONSTRAINT chk_schedules_dates
        CHECK (end_date IS NULL OR end_date >= start_date),

    INDEX idx_schedules_medication (medication_id, is_active, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================
-- 4. TABLE: schedule_times
-- DAILY/INTERVAL : day_of_week = NULL
-- WEEKLY         : day_of_week 1-7 (ISO-8601, Senin=1 ... Minggu=7)
-- day_key adalah kolom generated agar kombinasi (jadwal, hari, jam)
-- tidak bisa duplikat meskipun day_of_week NULL. Kolom ini tidak perlu
-- dipetakan di Entity JPA.
-- =========================================
CREATE TABLE schedule_times (
    id             CHAR(36)    PRIMARY KEY,
    schedule_id    CHAR(36)    NOT NULL,
    scheduled_time TIME        NOT NULL,
    day_of_week    TINYINT,
    day_key        TINYINT GENERATED ALWAYS AS (IFNULL(day_of_week, 0)) STORED,
    created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_schedule_times_schedule
        FOREIGN KEY (schedule_id) REFERENCES medication_schedules(id) ON DELETE CASCADE,

    CONSTRAINT chk_day_of_week
        CHECK (day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7),

    CONSTRAINT uq_schedule_times_slot
        UNIQUE (schedule_id, day_key, scheduled_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================
-- 5. TABLE: medication_logs
-- Satu baris = satu kejadian (occurrence) minum obat.
-- * user_id, medication_name, dose_* adalah snapshot saat log dibuat,
--   sehingga riwayat tetap utuh walau obat/jadwal diubah atau dihapus.
-- * medication_id, schedule_id, schedule_time_id bersifat nullable dan
--   di-SET NULL jika induknya terhapus (log TIDAK ikut terhapus).
-- * uq_medication_occurrence menjamin generator idempotent.
-- * scheduled_at dan action_at dalam UTC.
-- =========================================
CREATE TABLE medication_logs (
    id               CHAR(36)     PRIMARY KEY,
    user_id          CHAR(36)     NOT NULL,
    medication_id    CHAR(36),
    schedule_id      CHAR(36),
    schedule_time_id CHAR(36),
    medication_name  VARCHAR(150) NOT NULL,
    dose_quantity    DECIMAL(8,2) NOT NULL,
    dose_unit        VARCHAR(20)  NOT NULL,
    scheduled_at     DATETIME(3)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    action_at        DATETIME(3),
    notes            TEXT,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                  ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_logs_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_logs_medication
        FOREIGN KEY (medication_id) REFERENCES medications(id) ON DELETE SET NULL,
    CONSTRAINT fk_logs_schedule
        FOREIGN KEY (schedule_id) REFERENCES medication_schedules(id) ON DELETE SET NULL,
    CONSTRAINT fk_logs_schedule_time
        FOREIGN KEY (schedule_time_id) REFERENCES schedule_times(id) ON DELETE SET NULL,

    CONSTRAINT uq_medication_occurrence
        UNIQUE (schedule_time_id, scheduled_at),

    CONSTRAINT chk_medication_log_status
        CHECK (status IN ('PENDING','TAKEN','SKIPPED','MISSED','CANCELLED')),

    -- list/adherence/dashboard per pengguna dan rentang waktu
    INDEX idx_logs_user_scheduled (user_id, scheduled_at),
    INDEX idx_logs_user_status    (user_id, status, scheduled_at),
    -- job pembuat MISSED dan job notifikasi
    INDEX idx_logs_status_scheduled (status, scheduled_at),
    -- membatalkan PENDING mendatang saat jadwal diubah/dihapus
    INDEX idx_logs_schedule (schedule_id, status, scheduled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================
-- 6. TABLE: notification_preferences
-- =========================================
CREATE TABLE notification_preferences (
    id                      CHAR(36)    PRIMARY KEY,
    user_id                 CHAR(36)    NOT NULL,
    notifications_enabled   BOOLEAN     NOT NULL DEFAULT TRUE,
    sound_enabled           BOOLEAN     NOT NULL DEFAULT TRUE,
    reminder_before_minutes INT         NOT NULL DEFAULT 0,
    created_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at              DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                        ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT uq_preferences_user UNIQUE (user_id),
    CONSTRAINT fk_preferences_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_reminder_before_minutes
        CHECK (reminder_before_minutes BETWEEN 0 AND 1440)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================
-- 7. TABLE: refresh_tokens
-- token_hash = SHA-256 (hex, 64 karakter) dari refresh token.
-- replaced_by_token_id dipakai untuk mendeteksi pemakaian ulang token
-- lama setelah rotasi (indikasi token dicuri).
-- =========================================
CREATE TABLE refresh_tokens (
    id                   CHAR(36)    PRIMARY KEY,
    user_id              CHAR(36)    NOT NULL,
    token_hash           CHAR(64)    NOT NULL,
    expires_at           DATETIME(3) NOT NULL,
    revoked_at           DATETIME(3),
    replaced_by_token_id CHAR(36),
    created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens(id) ON DELETE SET NULL,

    INDEX idx_refresh_tokens_user (user_id, revoked_at),
    INDEX idx_refresh_tokens_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================
-- 8. TABLE: devices  (BARU)
-- Perangkat pengguna untuk push notification (FCM).
-- =========================================
CREATE TABLE devices (
    id           CHAR(36)     PRIMARY KEY,
    user_id      CHAR(36)     NOT NULL,
    fcm_token    VARCHAR(512) NOT NULL,
    platform     VARCHAR(10)  NOT NULL,
    device_name  VARCHAR(100),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    last_seen_at DATETIME(3),
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                              ON UPDATE CURRENT_TIMESTAMP(3),

    CONSTRAINT uq_devices_fcm_token UNIQUE (fcm_token),
    CONSTRAINT fk_devices_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_devices_platform
        CHECK (platform IN ('ANDROID','IOS','WEB')),

    INDEX idx_devices_user (user_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =========================================
-- 9. TABLE: notification_logs
-- Satu baris = satu percobaan kirim notifikasi ke satu perangkat untuk
-- satu occurrence. user_id ditambahkan agar endpoint history tidak
-- perlu join berlapis.
-- =========================================
CREATE TABLE notification_logs (
    id                CHAR(36)    PRIMARY KEY,
    user_id           CHAR(36)    NOT NULL,
    device_id         CHAR(36),
    medication_log_id CHAR(36),
    notification_type VARCHAR(30) NOT NULL,
    delivery_status   VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    error_message     TEXT,
    sent_at           DATETIME(3),
    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    CONSTRAINT fk_notification_logs_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_logs_device
        FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE SET NULL,
    CONSTRAINT fk_notification_logs_medication_log
        FOREIGN KEY (medication_log_id) REFERENCES medication_logs(id) ON DELETE SET NULL,

    -- mencegah notifikasi ganda untuk occurrence + perangkat + tipe yang sama
    CONSTRAINT uq_notification_once
        UNIQUE (medication_log_id, device_id, notification_type),

    CONSTRAINT chk_notification_type
        CHECK (notification_type IN ('MEDICATION_REMINDER','PRE_REMINDER','MISSED_ALERT')),
    CONSTRAINT chk_notification_delivery_status
        CHECK (delivery_status IN ('QUEUED','SENT','FAILED')),

    INDEX idx_notification_logs_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
