# Notification API Spec

> Aturan umum (format response, status code, timezone, validasi) ada di [00-conventions.md](00-conventions.md).

Module ini mengelola preferensi pengingat, pendaftaran perangkat, dan riwayat pengiriman notifikasi. Pengiriman push notification membutuhkan integrasi Firebase Cloud Messaging (FCM). Semua endpoint memerlukan autentikasi.

## 1. Get Notification Preferences

Endpoint : `GET /api/notification-preferences`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Notification preferences retrieved successfully",
  "data": {
    "notificationsEnabled": true,
    "soundEnabled": true,
    "reminderBeforeMinutes": 0
  }
}
```

Aturan: preferensi default dibuat saat registrasi. Jika baris belum ada (data lama), backend membuatnya dengan nilai default saat pertama kali dibaca.

## 2. Update Notification Preferences

Endpoint : `PATCH /api/notification-preferences`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Request Body (semua field opsional) :
```json
{
  "notificationsEnabled": true,
  "soundEnabled": false,
  "reminderBeforeMinutes": 10
}
```

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Notification preferences updated successfully",
  "data": {
    "notificationsEnabled": true,
    "soundEnabled": false,
    "reminderBeforeMinutes": 10
  }
}
```

Response Body (Failed, 422) :
```json
{
  "success": false,
  "message": "Invalid notification preferences",
  "errors": {
    "reminderBeforeMinutes": "Value must be between 0 and 1440"
  }
}
```

Arti `reminderBeforeMinutes`: `0` berarti notifikasi hanya dikirim tepat waktu. Nilai lebih dari 0 menambah satu notifikasi `PRE_REMINDER` sejumlah menit sebelum waktu minum.

## 3. Register Device

Endpoint : `POST /api/devices`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Request Body :
```json
{
  "fcmToken": "FCM_REGISTRATION_TOKEN",
  "platform": "ANDROID",
  "deviceName": "Pixel 8"
}
```

Response Body (Success, 201) :
```json
{
  "success": true,
  "message": "Device registered successfully",
  "data": {
    "id": "DEVICE_UUID",
    "platform": "ANDROID",
    "deviceName": "Pixel 8",
    "isActive": true
  }
}
```

Response Body (Failed, 422) :
```json
{
  "success": false,
  "message": "Validation failed",
  "errors": {
    "platform": "Platform must be ANDROID, IOS, or WEB"
  }
}
```

Aturan:
- `fcmToken` wajib (maksimal 512 karakter), `platform` wajib enum, `deviceName` opsional (maksimal 100).
- Bersifat **upsert**: jika `fcmToken` sudah terdaftar, baris yang ada dipindahkan ke pengguna saat ini, diaktifkan kembali, dan `last_seen_at` diperbarui (satu perangkat fisik bisa berganti akun).
- Client memanggil endpoint ini setelah login dan setiap kali FCM menerbitkan token baru.
- Response `201` untuk perangkat baru dan `200` (dengan format yang sama) untuk token yang sudah terdaftar.

## 4. Remove Device

Endpoint : `DELETE /api/devices/{deviceId}`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Response Body (Success, 204) : Tidak ada response body.

Response Body (Failed, 404) :
```json
{
  "success": false,
  "message": "Device not found",
  "errors": null
}
```

Aturan: client memanggil ini saat logout agar perangkat berhenti menerima notifikasi pengguna tersebut. Perangkat milik pengguna lain → `404`.

## 5. Get Notification History

Endpoint : `GET /api/notifications/history?page=0&size=20`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Query Parameters :
- `page` (optional, default 0), `size` (optional, default 20, maksimum 100)
- `deliveryStatus` (optional: `QUEUED`, `SENT`, `FAILED`)

Urutan: `createdAt` terbaru lebih dulu.

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Notification history retrieved successfully",
  "data": {
    "items": [
      {
        "id": "NOTIFICATION_LOG_UUID",
        "medicationLogId": "LOG_UUID",
        "notificationType": "MEDICATION_REMINDER",
        "deliveryStatus": "SENT",
        "sentAt": "2026-11-11T08:00:02+07:00",
        "errorMessage": null,
        "createdAt": "2026-11-11T08:00:00+07:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalItems": 1,
    "totalPages": 1
  }
}
```

Catatan: endpoint membaca tabel `notification_logs` yang kini memiliki `user_id`, sehingga filter pengguna langsung dan tidak perlu join. `medicationLogId` bisa `null` jika log konsumsi terkait sudah dihapus.

## Aturan pengiriman (backend)

1. Scheduler berjalan tiap menit dan mencari `medication_logs` berstatus `PENDING` yang waktunya jatuh dalam jendela berikutnya.
2. Notifikasi dilewati jika `notificationsEnabled = false`.
3. Untuk `reminderBeforeMinutes > 0`, kirim `PRE_REMINDER` pada `scheduledAt - reminderBeforeMinutes`, lalu `MEDICATION_REMINDER` tepat pada `scheduledAt`.
4. Setiap percobaan kirim membuat satu baris `notification_logs` per perangkat aktif (`QUEUED` → `SENT` atau `FAILED`). Constraint `uq_notification_once` mencegah kiriman ganda.
5. Jika FCM menjawab token tidak valid atau tidak terdaftar, perangkat dinonaktifkan (`is_active = false`).
6. Nilai `soundEnabled` dikirim sebagai bagian dari payload notifikasi.
