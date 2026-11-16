# Interval

**Interval** adalah aplikasi pengingat minum obat yang namanya diambil dari konsep jeda waktu yang konsisten. Saat sedang menjalani pengobatan, ketepatan waktu antar-dosis sangat menentukan seberapa efektif obat bekerja di dalam tubuh. Di balik itu, interval juga berarti memberi ruang sejenak di tengah padatnya aktivitas untuk peduli pada kesehatan. Aplikasi ini hadir agar rutinitas minum obat terasa lebih ringan, teratur, dan terkontrol. Anda cukup mencatat daftar obat beserta jadwalnya, lalu Interval akan mengingatkan tepat waktu melalui notifikasi. Setelah itu, Anda bisa menandai apakah dosis sudah diminum atau terlewat, dan dari catatan tersebut Interval akan menghitung tingkat kedisiplinan pengobatan (*adherence*) Anda secara otomatis.

## Fitur

| Area | Yang bisa dilakukan |
|---|---|
| **Akun** | Daftar, login, refresh token dengan rotasi, logout satu perangkat atau semua perangkat, ubah profil dan timezone, ganti password |
| **Obat** | Tambah, ubah, jeda/lanjutkan, dan hapus obat (hapus bersifat *soft delete*, riwayat tetap utuh) |
| **Jadwal** | Harian (`DAILY`), mingguan per hari (`WEEKLY`), atau setiap N hari (`INTERVAL`), dengan satu atau lebih jam per jadwal |
| **Pelacakan** | Tandai dosis `TAKEN` atau `SKIPPED`, batalkan, lihat riwayat dengan filter, hitung adherence |
| **Dashboard** | Ringkasan hari ini dan adherence 7 hari, daftar dosis hari ini |
| **Notifikasi** | Push notification (FCM) tepat waktu dan pengingat dini, preferensi (aktif, suara, menit sebelum), daftar perangkat, riwayat pengiriman |
| **Keamanan** | JWT berumur pendek, deteksi pencurian refresh token, pembatasan percobaan login (429), data hanya bisa diakses pemiliknya |

## Cara kerja singkat

Tiga konsep ini membantu memahami seluruh API:

1. **Jadwal adalah aturan, bukan kejadian.** "Parasetamol setiap hari pukul 08:00 dan 20:00" adalah satu jadwal. Kejadian nyatanya disebut *occurrence* dan disimpan di tabel `medication_logs`.
2. **Occurrence dibuat otomatis** untuk 48 jam ke depan, saat jadwal dibuat atau diubah dan oleh job berkala yang menjaga jendela itu terus bergulir. Setiap occurrence punya status:

   ```
   PENDING ──(pengguna)──> TAKEN / SKIPPED
      │
      ├──(60 menit tanpa tindakan)──> MISSED   (masih bisa ditandai TAKEN/SKIPPED terlambat)
      └──(jadwal/obat diubah, dijeda, dihapus)──> CANCELLED
   ```
3. **Waktu.** Database menyimpan semua waktu dalam UTC. API mengembalikan waktu dengan offset timezone pengguna, misalnya `2026-11-12T20:00:00+07:00`, dan "hari ini" selalu berarti tanggal lokal pengguna.

Adherence dihitung sebagai `taken / (taken + skipped + missed) × 100`. Dosis yang belum waktunya (`PENDING`) dan yang dibatalkan (`CANCELLED`) tidak ikut dihitung.

## Teknologi

- Java 21 dan Spring Boot (versi persisnya ada di `pom.xml`): Web, Security, Data JPA, Validation, Actuator
- MySQL 8.4 dengan migrasi skema Flyway
- JWT (jjwt) dan BCrypt
- Firebase Cloud Messaging (Firebase Admin SDK)
- springdoc-openapi (Swagger UI)
- Testcontainers untuk integration test
- Docker dan Docker Compose

## Memulai

### Prasyarat

| Kebutuhan | Untuk apa |
|---|---|
| Git | Mengambil kode |
| Docker dan Docker Compose | Menjalankan lewat Compose, dan **wajib untuk menjalankan test** (Testcontainers) |
| JDK 21 | Hanya jika menjalankan tanpa Compose atau menjalankan test dari terminal |
| Proyek Firebase dengan berkas *service account* | Hanya untuk notifikasi sungguhan (profil `prod`) 

### 1. Clone

```bash
git clone https://github.com/ymrief/Project-Interval-API-Backend.git
cd Project-Interval-API-Backend
```

### 2A. Docker Compose

Menjalankan aplikasi dan MySQL sekaligus.

**1) Siapkan konfigurasi**

```bash
cp .env.example .env
```

Buka `.env` dan isi:

| Variabel | Isi |
|---|---|
| `MYSQL_ROOT_PASSWORD` | Password root MySQL (bukan dipakai aplikasi) |
| `DB_PASSWORD` | Password user database milik aplikasi |
| `JWT_SECRET` | Minimal 32 karakter acak, buat dengan `openssl rand -base64 48` |
| `SPRING_PROFILES_ACTIVE` | `dev` untuk mencoba tanpa Firebase, atau `prod` (bawaan) |
| `SWAGGER_ENABLED` | `true` jika ingin membuka Swagger UI pada profil `prod` |

**2) Pilih mode**

| | `dev` (coba-coba) | `prod` |
|---|---|---|
| Firebase | Tidak perlu | **Wajib**: letakkan berkas di `./secrets/firebase-service-account.json` |
| Notifikasi | Hanya dicatat di log, **tidak sampai ke perangkat** | Dikirim lewat FCM |
| Swagger UI | Aktif | Mati, kecuali `SWAGGER_ENABLED=true` |
| Konfigurasi tidak aman | Diizinkan | Aplikasi **menolak start** (secret lemah, FCM mati) |

Untuk `prod`:

```bash
mkdir -p secrets
# salin berkas service account Firebase ke secrets/firebase-service-account.json
```

**3) Jalankan**

```bash
docker compose up --build
```

Build pertama membutuhkan beberapa menit. Aplikasi baru dijalankan setelah MySQL dinyatakan sehat, dan Flyway membuat seluruh tabel otomatis.

**4) Periksa**

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP", ...}
```

Swagger UI (jika aktif): <http://localhost:8080/swagger-ui.html>

Perintah yang berguna:

```bash
docker compose logs -f app     # log aplikasi
docker compose down            # berhenti (data database tetap tersimpan)
docker compose down -v         # berhenti dan HAPUS data database
```

### 2B. Tanpa Compose

Cocok saat mengubah kode. Aplikasi berjalan di mesin Anda dan hanya MySQL yang di Docker.

**1) Jalankan MySQL**

```bash
docker run -d --name interval-mysql \
  -e MYSQL_ALLOW_EMPTY_PASSWORD=yes \
  -e MYSQL_DATABASE=interval_db \
  -p 3306:3306 \
  mysql:8.4 \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci --default-time-zone=+00:00
```

Profil `dev` (bawaan) terhubung ke `localhost:3306` sebagai `root` tanpa password, jadi tidak ada yang perlu diatur lagi. Jika memakai MySQL sendiri, ubah lewat variabel `DB_URL`, `DB_USERNAME`, dan `DB_PASSWORD`.

**2) Jalankan aplikasi**

```bash
./mvnw spring-boot:run
```

Swagger UI: <http://localhost:8080/swagger-ui.html>

### 3. Mencoba API

Cara termudah: buka Swagger UI, jalankan `POST /api/auth/register` lalu `POST /api/auth/login`, tekan tombol **Authorize** dan tempel `accessToken`. Setelah itu semua endpoint bisa dicoba dari browser.

Lewat terminal, alur lengkapnya:

```bash
BASE=http://localhost:8080

# 1. Daftar (timezone memengaruhi jam jadwal dan "hari ini")
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d '{"fullName":"John Doe","email":"john@example.com","password":"StrongPassword123","timezone":"Asia/Jakarta"}'

# 2. Login, lalu salin accessToken dari respons
curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"john@example.com","password":"StrongPassword123"}'
export TOKEN=<accessToken>

# 3. Tambah obat, lalu salin id dari respons
curl -s -X POST $BASE/api/medications -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"Paracetamol","dosage":"500 mg","dosageForm":"TABLET","instructions":"Diminum setelah makan"}'
export MED=<id obat>

# 4. Buat jadwal harian pukul 08:00 dan 20:00 (ganti startDate dengan tanggal hari ini)
curl -s -X POST $BASE/api/medications/$MED/schedules -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"frequencyType":"DAILY","doseQuantity":1,"doseUnit":"TABLET","startDate":"2026-11-16","times":[{"scheduledTime":"08:00:00"},{"scheduledTime":"20:00:00"}]}'

# 5. Lihat dosis hari ini (occurrence sudah dibuat otomatis), salin logId salah satunya
curl -s $BASE/api/dashboard/today -H "Authorization: Bearer $TOKEN"
export LOG=<logId>

# 6. Tandai sudah diminum
curl -s -X PATCH $BASE/api/medication-logs/$LOG -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"status":"TAKEN","notes":"Sudah makan"}'

# 7. Ringkasan dan adherence
curl -s $BASE/api/dashboard/summary -H "Authorization: Bearer $TOKEN"
```

Semua respons berbentuk `{ "success": ..., "message": ..., "data": ... }`, dan kegagalan berbentuk `{ "success": false, "message": ..., "errors": ... }`. Aturan lengkapnya ada di [`docs/api/00-conventions.md`](docs/api/00-conventions.md).

---

## Konfigurasi

Aplikasi memakai dua profil:

| Profil | Aktif ketika | Catatan |
|---|---|---|
| `dev` | Bawaan (juga dipakai test) | Database lokal, SQL dicetak, FCM mati, Swagger aktif |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | Semua rahasia dari environment variable tanpa nilai bawaan, Swagger mati, FCM wajib |

Variabel environment utama untuk `prod`:

| Variabel | Wajib | Keterangan |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Ya | Koneksi database (user khusus aplikasi, bukan root) |
| `JWT_SECRET` | Ya | Minimal 32 karakter acak |
| `FCM_CREDENTIALS_PATH` | Ya | Path berkas service account Firebase |
| `SWAGGER_ENABLED` | Tidak | `true` untuk membuka Swagger |

Daftar lengkap, penjelasan job terjadwal, pembatasan login, dan **daftar periksa sebelum production** ada di [`docs/operations.md`](docs/operations.md).

## Dokumentasi

| Dokumen | Isi |
|---|---|
| [`docs/api/00-conventions.md`](docs/api/00-conventions.md) | Aturan umum: format respons, kode status, waktu dan timezone, pagination, semantik `PATCH`, daftar endpoint |
| [`docs/api/auth.md`](docs/api/auth.md), [`user.md`](docs/api/user.md), [`medication.md`](docs/api/medication.md), [`schedule.md`](docs/api/schedule.md), [`tracking.md`](docs/api/tracking.md), [`dashboard.md`](docs/api/dashboard.md), [`notification.md`](docs/api/notification.md) | Spesifikasi endpoint per modul |
| [`docs/operations.md`](docs/operations.md) | Operasional dan keamanan production |
| Swagger UI | Dokumentasi interaktif di `/swagger-ui.html` |
