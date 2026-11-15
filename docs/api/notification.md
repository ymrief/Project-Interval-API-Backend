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
    "platform": "Value must be one of: ANDROID, IOS, WEB"
  }
}
```

Aturan:
- `fcmToken` wajib (maksimal 512 karakter), `platform` wajib enum, `deviceName` opsional (maksimal 100).
- Bersifat **upsert**: jika `fcmToken` sudah terdaftar, baris yang ada dipindahkan ke pengguna saat ini, diaktifkan kembali, dan `last_seen_at` diperbarui (satu perangkat fisik bisa berganti akun).
- Client memanggil endpoint ini setelah login dan setiap kali FCM menerbitkan token baru.
- Response `201` (`Device registered successfully`) untuk perangkat baru dan `200` (`Device updated successfully`, bentuk data sama) untuk token yang sudah terdaftar.
- `fcmToken` tidak pernah dikembalikan oleh API: itu kredensial perangkat. `deviceName` yang tidak dikirim tidak mengubah nama yang tersimpan.

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

Nilai `deliveryStatus` di luar enum menghasilkan `400`; `page` negatif atau `size` di luar 1–100 menghasilkan `422`.

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

Job berjalan tiap menit (`interval.notification.dispatch-interval`) dan memproses occurrence `PENDING`:

1. Notifikasi hanya untuk pengguna dengan `notificationsEnabled = true`.
2. **`MEDICATION_REMINDER`** dikirim jika `scheduledAt` berada dalam rentang `[sekarang - batas MISSED, sekarang]` (batas MISSED default 60 menit). Batas bawah mencegah pengingat basi bila job sempat berhenti lama.
3. **`PRE_REMINDER`** hanya jika `reminderBeforeMinutes > 0` dan dosis tinggal kurang dari atau sama dengan `reminderBeforeMinutes` menit lagi. Jika waktu dosis sudah tiba atau lewat, pengingat dini dilewati.
4. Satu percobaan kirim = satu baris `notification_logs` per (occurrence, perangkat aktif, tipe). Baris dicatat `QUEUED` **sebelum** mengirim, lalu menjadi `SENT` atau `FAILED`. `uq_notification_once` menjamin tidak ada kiriman ganda, termasuk bila aplikasi berjalan di beberapa instance.
5. Percobaan yang `FAILED` **tidak diulang otomatis**. Baris `QUEUED` yang tertinggal karena aplikasi mati di tengah pengiriman juga tidak diulang.
6. Jika FCM menjawab token tidak valid (`UNREGISTERED` atau `SENDER_ID_MISMATCH`), perangkat dinonaktifkan (`isActive = false`). Perangkat aktif kembali saat aplikasi mendaftarkan token yang sama lagi lewat `POST /api/devices`. Kegagalan lain (mis. layanan sedang bermasalah) tidak menonaktifkan perangkat.
7. `soundEnabled` dikirim sebagai bagian payload (`data.sound`) dan memengaruhi suara notifikasi Android/iOS.
8. Teks notifikasi berbahasa Indonesia. Payload `data` berisi `type`, `medicationLogId`, `scheduledAt` (UTC), dan `sound`, agar aplikasi dapat langsung membuka log terkait.
9. `MISSED_ALERT` tersedia sebagai tipe tetapi **belum dikirim** oleh backend.

Mode pengembangan: bila `interval.notification.fcm.enabled=false` (default), push hanya dicatat di log dan dianggap `SENT`, **tanpa** benar-benar dikirim. Di production wajib `true` dengan `interval.notification.fcm.credentials-path` menunjuk berkas service account Firebase.
