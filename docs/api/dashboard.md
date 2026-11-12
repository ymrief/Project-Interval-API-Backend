# Dashboard API Spec

> Aturan umum (format response, status code, timezone, validasi) ada di [00-conventions.md](00-conventions.md).

Semua endpoint memerlukan autentikasi. "Hari ini" selalu berarti **tanggal lokal pengguna** (timezone dari profil), dihitung dari 00:00:00 sampai 23:59:59.999 waktu lokal lalu dikonversi ke UTC untuk query. Occurrence berstatus `CANCELLED` tidak pernah dihitung.

## 1. Get Dashboard Summary

Endpoint : `GET /api/dashboard/summary`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Dashboard summary retrieved successfully",
  "data": {
    "activeMedicationCount": 3,
    "todayScheduledCount": 6,
    "todayTakenCount": 2,
    "todaySkippedCount": 0,
    "todayPendingCount": 3,
    "todayMissedCount": 1,
    "weeklyAdherencePercentage": 80.00
  }
}
```

Definisi field:

| Field | Arti |
|---|---|
| `activeMedicationCount` | Obat dengan `isActive = true` dan belum dihapus |
| `todayScheduledCount` | Semua occurrence hari ini selain `CANCELLED`. Selalu sama dengan jumlah `taken + skipped + pending + missed` hari ini (pada contoh: 2 + 0 + 3 + 1 = 6) |
| `todayTakenCount`, `todaySkippedCount`, `todayPendingCount`, `todayMissedCount` | Jumlah occurrence hari ini per status |
| `weeklyAdherencePercentage` | Adherence **7 hari terakhir termasuk hari ini**, memakai rumus adherence di konvensi. `null` jika belum ada occurrence yang selesai |

Catatan: persentase sengaja dihitung mingguan, bukan harian, karena di awal hari belum ada dosis yang selesai sehingga persentase harian tidak bermakna.

## 2. Get Today's Medication Schedule

Endpoint : `GET /api/dashboard/today`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Today's schedule retrieved successfully",
  "data": [
    {
      "logId": "LOG_UUID",
      "medicationId": "MEDICATION_UUID",
      "medicationName": "Paracetamol",
      "dosage": "500 mg",
      "doseQuantity": 1,
      "doseUnit": "TABLET",
      "scheduledAt": "2026-11-11T08:00:00+07:00",
      "status": "PENDING",
      "actionAt": null,
      "instructions": "Diminum setelah makan"
    }
  ]
}
```

Aturan:
- Data berasal dari occurrence di `medication_logs` untuk tanggal lokal pengguna, bukan sekadar semua jam yang tersimpan di `schedule_times`.
- Diurutkan berdasarkan `scheduledAt` naik. Tidak ada pagination (jumlahnya terbatas satu hari). Jika tidak ada jadwal, `data` berupa array kosong.
- `medicationName`, `doseQuantity`, `doseUnit` berasal dari snapshot log. `dosage` dan `instructions` diambil dari obat saat ini; keduanya `null` jika obat sudah dihapus permanen.
- Untuk menandai obat sudah diminum atau dilewati, client memakai `PATCH /api/medication-logs/{logId}` dengan `logId` dari response ini.
