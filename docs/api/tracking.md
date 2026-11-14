# Medication Tracking API Spec

> Aturan umum (format response, status code, timezone, validasi) ada di [00-conventions.md](00-conventions.md).

Module ini mencatat setiap occurrence jadwal dan status konsumsi obat. Semua endpoint memerlukan autentikasi dan hanya dapat mengakses log milik pengguna yang sedang login (log milik orang lain → `404`).

## Siklus hidup status

Status yang diperbolehkan: `PENDING`, `TAKEN`, `SKIPPED`, `MISSED`, `CANCELLED`.

| Status | Dibuat oleh | Arti |
|---|---|---|
| `PENDING` | Job generator | Menunggu waktunya atau belum ada tindakan |
| `TAKEN` | Pengguna | Obat sudah diminum |
| `SKIPPED` | Pengguna | Sengaja dilewati |
| `MISSED` | Job backend | `PENDING` yang melewati batas toleransi (default 60 menit setelah `scheduledAt`, dapat dikonfigurasi) |
| `CANCELLED` | Backend | Dibatalkan karena jadwal/obat diubah, dijeda, atau dihapus |

## Pembuatan occurrence

Log `PENDING` dibuat otomatis oleh backend, bukan oleh client.

- **Jendela (horizon):** occurrence dibuat untuk 48 jam ke depan (`interval.occurrence.horizon`). Job berkala (default tiap jam) menjaga jendela ini tetap bergulir.
- **Tidak ada backfill:** occurrence tidak dibuat untuk waktu yang sudah lewat. Jadwal yang dibuat pukul 17:00 dengan jam 08:00 tidak menghasilkan log untuk 08:00 hari itu.
- **Jam dinding:** jam jadwal dibaca di timezone pengguna lalu disimpan sebagai UTC. Pada hari DST, jam yang tidak ada digeser maju dan jam ganda memakai offset yang lebih awal.
- **INTERVAL:** dihitung dari `startDate` (acuan tetap), bukan dari hari ini.
- **Penyelarasan:** setiap perubahan (jadwal dibuat/diubah/dihapus, obat dijeda/dilanjutkan/dihapus, timezone berubah) menjalankan penyelarasan yang idempotent. Log `TAKEN`, `SKIPPED`, dan `MISSED` tidak pernah disentuh.
- **MISSED:** job berkala (default tiap menit) mengubah `PENDING` menjadi `MISSED` jika `scheduledAt` lebih tua dari batas toleransi (`interval.occurrence.missed-after`, default 60 menit; perbandingan ketat).

## Bentuk objek Medication Log

| Field | Keterangan |
|---|---|
| `id` | UUID log |
| `medicationId` | UUID obat, bisa `null` jika obat sudah dihapus permanen |
| `medicationName` | Nama obat **saat log dibuat** (snapshot) |
| `scheduleId`, `scheduleTimeId` | Bisa `null` jika jadwal diganti atau dihapus |
| `doseQuantity`, `doseUnit` | Dosis saat log dibuat (snapshot) |
| `scheduledAt` | Waktu seharusnya minum |
| `status` | Lihat tabel di atas |
| `actionAt` | Waktu status diubah pengguna; `null` untuk `PENDING`, `MISSED`, `CANCELLED` |
| `notes` | Catatan pengguna |

## 1. Get Medication Logs

Endpoint : `GET /api/medication-logs?from=2026-11-01&to=2026-11-30&status=TAKEN&medicationId=MEDICATION_UUID&page=0&size=20`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Query Parameters :
- `from`, `to` (optional, tanggal lokal pengguna, inklusif). Jika kosong: 30 hari terakhir sampai hari ini
- `status` (optional)
- `medicationId` (optional)
- `page` (optional, default 0), `size` (optional, default 20, maksimum 100)

Urutan: `scheduledAt` terbaru lebih dulu.

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Medication logs retrieved successfully",
  "data": {
    "items": [
      {
        "id": "LOG_UUID",
        "medicationId": "MEDICATION_UUID",
        "medicationName": "Paracetamol",
        "scheduleId": "SCHEDULE_UUID",
        "scheduleTimeId": "SCHEDULE_TIME_UUID",
        "doseQuantity": 1,
        "doseUnit": "TABLET",
        "scheduledAt": "2026-11-11T08:00:00+07:00",
        "status": "TAKEN",
        "actionAt": "2026-11-11T08:05:00+07:00",
        "notes": "Diminum setelah sarapan"
      }
    ],
    "page": 0,
    "size": 20,
    "totalItems": 1,
    "totalPages": 1
  }
}
```

Response Body (Failed, 422) :
```json
{
  "success": false,
  "message": "Invalid date range",
  "errors": {
    "to": "End date must not be earlier than start date"
  }
}
```

Rentang `from`–`to` maksimal 366 hari.

## 2. Get Medication Log Detail

Endpoint : `GET /api/medication-logs/{logId}`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Medication log retrieved successfully",
  "data": {
    "id": "LOG_UUID",
    "medicationId": "MEDICATION_UUID",
    "medicationName": "Paracetamol",
    "scheduleId": "SCHEDULE_UUID",
    "scheduleTimeId": "SCHEDULE_TIME_UUID",
    "doseQuantity": 1,
    "doseUnit": "TABLET",
    "scheduledAt": "2026-11-11T08:00:00+07:00",
    "status": "TAKEN",
    "actionAt": "2026-11-11T08:05:00+07:00",
    "notes": "Diminum setelah sarapan"
  }
}
```

Response Body (Failed, 404) :
```json
{
  "success": false,
  "message": "Medication log not found",
  "errors": null
}
```

## 3. Update Medication Log Status

Endpoint : `PATCH /api/medication-logs/{logId}`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Request Body :
```json
{
  "status": "TAKEN",
  "notes": "Sudah diminum setelah makan"
}
```

`status` wajib. `notes` opsional (jika dikirim `null`, catatan dikosongkan).

Response Body (Success, 200) : objek Medication Log lengkap.
```json
{
  "success": true,
  "message": "Medication log updated successfully",
  "data": {
    "id": "LOG_UUID",
    "medicationId": "MEDICATION_UUID",
    "medicationName": "Paracetamol",
    "scheduleId": "SCHEDULE_UUID",
    "scheduleTimeId": "SCHEDULE_TIME_UUID",
    "doseQuantity": 1,
    "doseUnit": "TABLET",
    "scheduledAt": "2026-11-11T08:00:00+07:00",
    "status": "TAKEN",
    "actionAt": "2026-11-11T08:05:00+07:00",
    "notes": "Sudah diminum setelah makan"
  }
}
```

Response Body (Failed, 422) :
```json
{
  "success": false,
  "message": "Invalid status transition",
  "errors": {
    "status": "Cannot change status from CANCELLED to TAKEN"
  }
}
```

**Transisi status yang diizinkan untuk client:**

| Dari \ Ke | `PENDING` | `TAKEN` | `SKIPPED` |
|---|---|---|---|
| `PENDING` | – | ✓ | ✓ |
| `TAKEN` | ✓ (batalkan, lihat syarat) | – | ✓ |
| `SKIPPED` | ✓ (batalkan, lihat syarat) | ✓ | – |
| `MISSED` | ✗ | ✓ (diminum terlambat) | ✓ |
| `CANCELLED` | ✗ | ✗ | ✗ |

Aturan:
- Client **tidak boleh** mengirim `MISSED` atau `CANCELLED`; keduanya hanya diatur backend. Nilai di luar enum juga `422`.
- Kembali ke `PENDING` hanya diizinkan selama waktu sekarang belum melewati batas toleransi `MISSED` (`scheduledAt` + toleransi). Jika sudah lewat, tolak dengan `422`.
- Backend mengisi `actionAt` dengan waktu sekarang saat status berubah ke `TAKEN` atau `SKIPPED`, dan mengosongkannya saat kembali ke `PENDING`.
- Client tidak boleh mengubah `scheduleId`, `scheduleTimeId`, `scheduledAt`, atau snapshot dosis.

## 4. Get Medication Adherence

Endpoint : `GET /api/medication-logs/adherence?from=2026-11-01&to=2026-11-30`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Query Parameters :
- `from`, `to` (optional, tanggal lokal pengguna, inklusif). Jika kosong: 30 hari terakhir sampai hari ini. Maksimal 366 hari.
- `medicationId` (optional)

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Medication adherence retrieved successfully",
  "data": {
    "totalScheduled": 60,
    "taken": 52,
    "skipped": 3,
    "missed": 5,
    "pending": 0,
    "adherencePercentage": 86.67
  }
}
```

Response Body (Failed, 422) :
```json
{
  "success": false,
  "message": "Invalid date range",
  "errors": {
    "to": "End date must not be earlier than start date"
  }
}
```

Definisi:
- `totalScheduled` = seluruh occurrence pada rentang tersebut **kecuali** `CANCELLED` (jadi `taken + skipped + missed + pending`).
- `adherencePercentage = taken / (taken + skipped + missed) * 100`. `PENDING` dan `CANCELLED` tidak masuk penyebut. Jika penyebut 0, nilainya `null`.
- Pada contoh: 52 / (52 + 3 + 5) = 86,67.
