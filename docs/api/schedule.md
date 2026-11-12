# Schedule API Spec

> Aturan umum (format response, status code, timezone, validasi) ada di [00-conventions.md](00-conventions.md).

Semua endpoint memerlukan autentikasi dan hanya dapat mengakses jadwal dari obat milik pengguna yang sedang login (milik orang lain atau sudah dihapus → `404`).

Jadwal hanyalah **aturan**. Kejadian nyata (occurrence) dibuat oleh job backend ke tabel `medication_logs` dan dilacak lewat modul Tracking.

## Bentuk objek Schedule

Semua endpoint mengembalikan objek yang sama:

```json
{
  "id": "SCHEDULE_UUID",
  "medicationId": "MEDICATION_UUID",
  "frequencyType": "DAILY",
  "intervalDays": null,
  "doseQuantity": 1,
  "doseUnit": "TABLET",
  "startDate": "2026-11-11",
  "endDate": null,
  "isActive": true,
  "times": [
    {
      "id": "SCHEDULE_TIME_UUID",
      "scheduledTime": "08:00:00",
      "dayOfWeek": null
    }
  ]
}
```

Timezone jadwal mengikuti timezone pengguna (`/api/users/me`), jadi tidak ada field `timezone` di jadwal.

## Aturan validasi

| Aturan | Keterangan |
|---|---|
| `frequencyType` | `DAILY`, `WEEKLY`, atau `INTERVAL` |
| DAILY | Setiap `dayOfWeek` harus `null`; obat diminum setiap hari pada jam-jam di `times` |
| WEEKLY | Setiap `dayOfWeek` wajib 1–7 (ISO-8601, Senin=1 … Minggu=7). Satu baris `times` mewakili satu kombinasi hari dan jam |
| INTERVAL | `intervalDays` wajib minimal 1, `dayOfWeek` harus `null`. `startDate` menjadi acuan: diminum pada `startDate`, lalu setiap `intervalDays` hari |
| `doseQuantity` | Harus lebih dari 0 |
| `doseUnit` | `TABLET`, `CAPSULE`, `ML`, `DROP`, `OTHER` |
| `endDate` | Opsional, tidak boleh lebih awal dari `startDate` |
| `times` | Minimal 1, maksimal 50 baris. Kombinasi (`dayOfWeek`, `scheduledTime`) tidak boleh duplikat |
| Obat | Harus milik pengguna, belum dihapus, dan `isActive = true` untuk membuat jadwal baru |

Pelanggaran semua aturan di atas dikembalikan sebagai `422`.

## 1. Get Schedule List

Endpoint : `GET /api/schedules?medicationId=MEDICATION_UUID&active=true`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Query Parameters :
- `medicationId` (optional)
- `active` (optional, filter `isActive`; jadwal yang sudah dihapus tidak pernah ditampilkan)

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Schedule list retrieved successfully",
  "data": [
    {
      "id": "SCHEDULE_UUID",
      "medicationId": "MEDICATION_UUID",
      "frequencyType": "DAILY",
      "intervalDays": null,
      "doseQuantity": 1,
      "doseUnit": "TABLET",
      "startDate": "2026-11-11",
      "endDate": null,
      "isActive": true,
      "times": [
        {
          "id": "SCHEDULE_TIME_UUID",
          "scheduledTime": "08:00:00",
          "dayOfWeek": null
        }
      ]
    }
  ]
}
```

## 2. Create Schedule

Endpoint : `POST /api/medications/{medicationId}/schedules`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Request Body :
```json
{
  "frequencyType": "DAILY",
  "intervalDays": null,
  "doseQuantity": 1,
  "doseUnit": "TABLET",
  "startDate": "2026-11-11",
  "endDate": null,
  "times": [
    { "scheduledTime": "08:00:00", "dayOfWeek": null },
    { "scheduledTime": "20:00:00", "dayOfWeek": null }
  ]
}
```

Contoh `times` untuk WEEKLY (Senin dan Kamis pukul 08:00):
```json
"times": [
  { "scheduledTime": "08:00:00", "dayOfWeek": 1 },
  { "scheduledTime": "08:00:00", "dayOfWeek": 4 }
]
```

Response Body (Success, 201) : objek Schedule lengkap.
```json
{
  "success": true,
  "message": "Schedule created successfully",
  "data": {
    "id": "SCHEDULE_UUID",
    "medicationId": "MEDICATION_UUID",
    "frequencyType": "DAILY",
    "intervalDays": null,
    "doseQuantity": 1,
    "doseUnit": "TABLET",
    "startDate": "2026-11-11",
    "endDate": null,
    "isActive": true,
    "times": [
      { "id": "SCHEDULE_TIME_UUID_1", "scheduledTime": "08:00:00", "dayOfWeek": null },
      { "id": "SCHEDULE_TIME_UUID_2", "scheduledTime": "20:00:00", "dayOfWeek": null }
    ]
  }
}
```

Response Body (Failed, 404) :
```json
{
  "success": false,
  "message": "Medication not found",
  "errors": null
}
```

Response Body (Failed, 422) :
```json
{
  "success": false,
  "message": "Invalid schedule configuration",
  "errors": {
    "times": "At least one time is required"
  }
}
```

Aturan:
- Setelah jadwal tersimpan, backend langsung membuat occurrence `PENDING` untuk horizon ke depan (1–2 hari).
- Occurrence **tidak dibuat mundur**. Jika `startDate` di masa lalu, occurrence dibuat mulai dari waktu sekarang.

## 3. Get Schedule Detail

Endpoint : `GET /api/schedules/{scheduleId}`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Schedule retrieved successfully",
  "data": {
    "id": "SCHEDULE_UUID",
    "medicationId": "MEDICATION_UUID",
    "frequencyType": "WEEKLY",
    "intervalDays": null,
    "doseQuantity": 1,
    "doseUnit": "TABLET",
    "startDate": "2026-11-11",
    "endDate": "2026-12-11",
    "isActive": true,
    "times": [
      { "id": "SCHEDULE_TIME_UUID", "scheduledTime": "08:00:00", "dayOfWeek": 1 }
    ]
  }
}
```

Response Body (Failed, 404) :
```json
{
  "success": false,
  "message": "Schedule not found",
  "errors": null
}
```

## 4. Update Schedule

Endpoint : `PATCH /api/schedules/{scheduleId}`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Request Body (semua field opsional) :
```json
{
  "frequencyType": "DAILY",
  "doseQuantity": 2,
  "doseUnit": "TABLET",
  "endDate": "2026-12-31",
  "isActive": true,
  "times": [
    { "scheduledTime": "09:00:00", "dayOfWeek": null }
  ]
}
```

Response Body (Success, 200) : objek Schedule lengkap.
```json
{
  "success": true,
  "message": "Schedule updated successfully",
  "data": {
    "id": "SCHEDULE_UUID",
    "medicationId": "MEDICATION_UUID",
    "frequencyType": "DAILY",
    "intervalDays": null,
    "doseQuantity": 2,
    "doseUnit": "TABLET",
    "startDate": "2026-11-11",
    "endDate": "2026-12-31",
    "isActive": true,
    "times": [
      { "id": "NEW_OR_EXISTING_TIME_UUID", "scheduledTime": "09:00:00", "dayOfWeek": null }
    ]
  }
}
```

Response Body (Failed, 422) :
```json
{
  "success": false,
  "message": "Invalid schedule configuration",
  "errors": {
    "endDate": "End date must not be earlier than start date"
  }
}
```

Aturan:
- Validasi dilakukan terhadap **hasil gabungan** data lama dan field yang dikirim (misalnya mengubah `frequencyType` ke `WEEKLY` tanpa mengirim `times` baru → `422`).
- Jika `times` dikirim, daftar tersebut **menggantikan seluruh** jam yang ada. Baris yang sama persis (hari dan jam) mempertahankan `id`-nya.
- `startDate` tidak dapat diubah. Untuk memulai ulang dari tanggal lain, hapus jadwal dan buat yang baru.

**Kebijakan perubahan terhadap occurrence:**

| Occurrence | Perlakuan |
|---|---|
| `TAKEN`, `SKIPPED`, `MISSED` | Tidak pernah diubah (riwayat) |
| `PENDING` dengan `scheduledAt` sudah lewat | Dibiarkan; job `MISSED` yang menanganinya |
| `PENDING` dengan `scheduledAt` di masa depan | Diubah menjadi `CANCELLED`, lalu dibuat ulang sesuai jadwal baru |
| `CANCELLED` | Tidak diubah |

## 5. Delete Schedule

Endpoint : `DELETE /api/schedules/{scheduleId}`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Response Body (Success, 204) : Tidak ada response body.

Response Body (Failed, 404) :
```json
{
  "success": false,
  "message": "Schedule not found",
  "errors": null
}
```

Aturan: soft delete (`deleted_at` diisi, `isActive = false`). Occurrence `PENDING` mendatang menjadi `CANCELLED`; riwayat tetap utuh.
