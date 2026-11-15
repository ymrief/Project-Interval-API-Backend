# Operasional Interval API

## Profil

| Profil | Kapan dipakai | Datasource | Catatan |
|---|---|---|---|
| `dev` | Bawaan (`spring.profiles.default=dev`); pengembangan lokal dan test | `localhost:3306/interval_db`, user `root` | SQL dicetak, Swagger aktif, FCM mati, secret JWT bawaan |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | Dari environment variable, tanpa nilai bawaan | Swagger mati, FCM wajib aktif, detail error disembunyikan |

Pada profil `prod`, variabel yang wajib diisi tidak punya nilai bawaan: jika ada yang kosong aplikasi **gagal start**. `ProductionSafetyCheck` juga menolak secret JWT yang pendek atau masih berupa nilai pengembangan, dan menolak `fcm.enabled=false`.

## Environment variable (profil `prod`)

| Variabel | Wajib | Keterangan |
|---|---|---|
| `DB_URL` | Ya | Contoh `jdbc:mysql://db:3306/interval_db?useSSL=false&serverTimezone=UTC` |
| `DB_USERNAME`, `DB_PASSWORD` | Ya | Gunakan user khusus aplikasi (bukan root); perlu hak DDL karena Flyway |
| `JWT_SECRET` | Ya | Minimal 32 karakter acak, mis. `openssl rand -base64 48`. Mengganti nilainya mengeluarkan semua pengguna dari access token yang beredar |
| `FCM_CREDENTIALS_PATH` | Ya | Path berkas service account Firebase di dalam container |
| `SWAGGER_ENABLED` | Tidak | `true` untuk membuka `/swagger-ui.html` (bawaan `false`) |
| `DB_POOL_MIN_IDLE`, `DB_POOL_MAX_SIZE` | Tidak | Bawaan 5 dan 20 |

Pengaturan aplikasi lain (semuanya punya nilai bawaan di `application.properties`): `interval.jwt.*`, `interval.occurrence.*`, `interval.security.login-rate-limit.*`, `interval.maintenance.*`, `interval.notification.*`.

## Menjalankan

**Lokal (profil dev)**

```bash
./mvnw spring-boot:run
# Swagger UI: http://localhost:8080/swagger-ui.html
```

**Docker Compose (aplikasi + MySQL)**

```bash
cp .env.example .env            # isi password dan JWT_SECRET
mkdir secrets                   # taruh firebase-service-account.json di sini (prod)
docker compose up --build
curl http://localhost:8080/actuator/health
```

Untuk mencoba tanpa Firebase, set `SPRING_PROFILES_ACTIVE=dev` di `.env`. Dalam mode itu push hanya dicatat di log dan **tidak pernah sampai ke perangkat**.

**Test**: `./mvnw test` (butuh Docker untuk Testcontainers).

## Health check

| Endpoint | Fungsi |
|---|---|
| `/actuator/health` | Status keseluruhan, termasuk koneksi database |
| `/actuator/health/liveness` | Proses hidup |
| `/actuator/health/readiness` | Siap menerima trafik (dipakai `HEALTHCHECK` Docker) |

Ketiganya publik dan tanpa detail. Endpoint actuator lain tidak diekspos.

## Job terjadwal

| Job | Jadwal | Fungsi |
|---|---|---|
| Pembuatan occurrence | 30 detik setelah start, lalu tiap jam | Menjaga jendela occurrence 48 jam ke depan |
| Penandaan MISSED | Tiap menit | `PENDING` yang lewat 60 menit menjadi `MISSED` |
| Pengiriman notifikasi | Tiap menit | Pengingat tepat waktu dan pengingat dini |
| Pembersihan refresh token | Harian 03:30 UTC | Membuang token yang kedaluwarsa lebih dari 7 hari |
| Pembersihan rate limiter | Tiap 5 menit | Membuang jendela yang sudah berakhir dari memori |

Semua job idempotent dan aman dijalankan di beberapa instance (occurrence dan notifikasi dilindungi constraint unik di database), sehingga ShedLock tidak diperlukan. Konsekuensinya hanya pekerjaan ganda yang sia-sia. Matikan semua job dengan `interval.scheduling.enabled=false`.

## Rate limiting login

- Dihitung hanya untuk percobaan **gagal**: 5 per email dan 20 per IP dalam jendela 15 menit. Login berhasil mengosongkan hitungan email.
- Setelah batas tercapai, login dijawab `429` dengan header `Retry-After` (detik). Pemeriksaan dilakukan **sebelum** password diverifikasi, dan email yang tidak terdaftar diperlakukan persis sama, sehingga tidak membocorkan keberadaan akun.
- Penyimpanan di memori per instance: dengan N instance batas efektif sampai N kali lipat dan hitungan hilang saat restart. Jika perlu ketat lintas instance, pindahkan penyimpanan ke Redis atau database.
- Di belakang reverse proxy, atur `server.forward-headers-strategy=native` agar IP klien benar. Tanpa proxy, jangan aktifkan (header `X-Forwarded-For` bisa dipalsukan).

## Daftar periksa sebelum production

1. `JWT_SECRET` acak dan kuat, disimpan di secret manager (bukan di repository atau image).
2. Berkas service account Firebase dipasang read-only; **kirim satu notifikasi nyata ke perangkat sungguhan** (daftarkan token, buat jadwal beberapa menit ke depan). `FcmPushSender` tidak dapat diuji otomatis.
3. User database khusus aplikasi, bukan root. MySQL 8.0.16 ke atas (CHECK constraint ditegakkan mulai versi itu).
4. TLS dihentikan di reverse proxy; batasi ukuran body request di proxy (mis. `client_max_body_size 1m` di nginx), karena aplikasi tidak membatasi ukuran JSON sebelum validasi.
5. Cadangan database terjadwal dan uji pemulihannya.
6. Pantau `/actuator/health` dan log ERROR. Peringatan `FCM is DISABLED` di log berarti notifikasi tidak terkirim.
7. Kebijakan retensi untuk `notification_logs` dan `medication_logs` belum ada; keduanya terus bertambah.
