# Konvensi API Interval

Dokumen ini adalah satu-satunya sumber aturan umum. Setiap file modul (`auth.md`, `user.md`, dst.) hanya berisi endpoint modul itu dan merujuk ke sini.

- Base URL: `http://localhost:8080`
- Format data: `application/json`
- Semua ID menggunakan UUID string.
- Endpoint yang memerlukan autentikasi menggunakan header `Authorization: Bearer <accessToken>`.
- Password tidak pernah dikembalikan oleh API. Backend menyimpan password dalam bentuk hash (BCrypt).

## Waktu dan timezone

- Database menyimpan semua waktu dalam **UTC**.
- API **mengeluarkan** waktu dalam ISO 8601 dengan offset timezone pengguna. Contoh: `2026-11-11T08:00:00+07:00`.
- API **menerima** waktu ISO 8601 dengan offset atau `Z`.
- Tanggal memakai format `YYYY-MM-DD`; jam memakai `HH:mm:ss`.
- Parameter tanggal pada query (`from`, `to`) adalah **tanggal lokal pengguna** (timezone dari profil). `from` mulai 00:00:00 dan `to` berakhir 23:59:59.999 pada tanggal tersebut.
- Timezone harus berupa nama IANA yang valid, misalnya `Asia/Jakarta`.
- Jam pada jadwal obat selalu dibaca dalam timezone pengguna. Jika pengguna mengganti timezone, occurrence `PENDING` mendatang dibuat ulang.

## Format response

Sukses:

```json
{ "success": true, "message": "...", "data": {} }
```

Gagal:

```json
{ "success": false, "message": "...", "errors": null }
```

- `errors` berupa objek `{ "namaField": "pesan" }` untuk kesalahan validasi field, atau `null` jika kesalahannya tidak terkait field tertentu.
- Response `204` tidak memiliki body (pengecualian dari format di atas).

Contoh response gagal validasi:

```json
{
  "success": false,
  "message": "Validation failed",
  "errors": {
    "email": "Email format is invalid"
  }
}
```

## HTTP status yang digunakan

| Status | Arti | Contoh |
|---|---|---|
| 200 | Request berhasil | |
| 201 | Data berhasil dibuat | |
| 204 | Berhasil tanpa response body | Delete |
| 400 | Request **tidak dapat diproses**: JSON rusak, tipe parameter salah, UUID pada path tidak valid | `page=abc`, body bukan JSON |
| 401 | Belum login atau token tidak valid/kedaluwarsa | |
| 403 | Akun dinonaktifkan | Login dengan akun `isActive=false` |
| 404 | Data tidak ditemukan **atau milik pengguna lain** | |
| 409 | Konflik data | Email sudah terdaftar |
| 422 | Request terbaca dengan benar tetapi **validasi field atau aturan bisnis gagal** | Nama obat kosong, transisi status tidak valid, `endDate` < `startDate` |
| 429 | Terlalu banyak request | Rate limit login |
| 500 | Kesalahan internal server | |

Aturan penting:

- **Kepemilikan data:** jika pengguna mengakses data milik pengguna lain, kembalikan `404`, bukan `403`, agar keberadaan data tidak bocor.
- **Validasi:** semua kegagalan validasi field dan aturan bisnis memakai `422`. `400` hanya untuk request yang bahkan tidak bisa dibaca.
- Setiap endpoint yang butuh autentikasi dapat mengembalikan `401`:

```json
{
  "success": false,
  "message": "Unauthorized",
  "errors": null
}
```

## Pagination

- `page` dimulai dari 0, `size` adalah jumlah data per halaman.
- `size` maksimum 100; default mengikuti masing-masing endpoint.
- Bentuk data:

```json
{
  "items": [],
  "page": 0,
  "size": 10,
  "totalItems": 0,
  "totalPages": 0
}
```

## Semantik PATCH

- Field yang **tidak dikirim** tidak diubah.
- Field yang dikirim `null` hanya diperbolehkan untuk field nullable (misalnya `notes`, `imageUrl`, `endDate`) dan akan mengosongkan nilainya. Untuk field wajib, `null` menghasilkan `422`.
- Field yang tidak dikenal atau tidak boleh diubah client (misalnya `id`, `userId`) diabaikan.

## Nilai enum

| Nama | Nilai |
|---|---|
| `dosageForm` | `TABLET`, `CAPSULE`, `SYRUP`, `DROPS`, `OTHER` |
| `frequencyType` | `DAILY`, `WEEKLY`, `INTERVAL` |
| `doseUnit` | `TABLET`, `CAPSULE`, `ML`, `DROP`, `OTHER` |
| `status` (log konsumsi) | `PENDING`, `TAKEN`, `SKIPPED`, `MISSED`, `CANCELLED` |
| `platform` (perangkat) | `ANDROID`, `IOS`, `WEB` |
| `notificationType` | `MEDICATION_REMINDER`, `PRE_REMINDER`, `MISSED_ALERT` |
| `deliveryStatus` | `QUEUED`, `SENT`, `FAILED` |

## Rumus adherence (dipakai Dashboard dan Tracking)

```
adherencePercentage = taken / (taken + skipped + missed) * 100
```

- `PENDING` dan `CANCELLED` **tidak** masuk penyebut. Dosis yang belum waktunya tidak boleh menurunkan persentase.
- Hasil dibulatkan 2 digit desimal.
- Jika penyebut 0, nilainya `null` (bukan 0 dan bukan error).

## Daftar endpoint

| Modul | Method | Path |
|---|---|---|
| Auth | POST | `/api/auth/register` |
| Auth | POST | `/api/auth/login` |
| Auth | POST | `/api/auth/refresh` |
| Auth | POST | `/api/auth/logout` |
| Auth | POST | `/api/auth/logout-all` |
| User | GET, PATCH | `/api/users/me` |
| User | PATCH | `/api/users/me/password` |
| Medication | GET, POST | `/api/medications` |
| Medication | GET, PATCH, DELETE | `/api/medications/{medicationId}` |
| Schedule | GET | `/api/schedules` |
| Schedule | POST | `/api/medications/{medicationId}/schedules` |
| Schedule | GET, PATCH, DELETE | `/api/schedules/{scheduleId}` |
| Tracking | GET | `/api/medication-logs` |
| Tracking | GET | `/api/medication-logs/adherence` |
| Tracking | GET, PATCH | `/api/medication-logs/{logId}` |
| Dashboard | GET | `/api/dashboard/summary` |
| Dashboard | GET | `/api/dashboard/today` |
| Notification | GET, PATCH | `/api/notification-preferences` |
| Notification | POST | `/api/devices` |
| Notification | DELETE | `/api/devices/{deviceId}` |
| Notification | GET | `/api/notifications/history` |
