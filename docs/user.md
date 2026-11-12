# User API Spec

> Aturan umum (format response, status code, timezone, validasi) ada di [00-conventions.md](00-conventions.md).

Semua endpoint memerlukan autentikasi. Identitas pengguna selalu diambil dari token, tidak pernah dari body atau path.

## 1. Get Current User

Endpoint : `GET /api/users/me`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "User profile retrieved successfully",
  "data": {
    "id": "USER_UUID",
    "fullName": "John Doe",
    "email": "john@example.com",
    "timezone": "Asia/Jakarta",
    "isActive": true,
    "createdAt": "2026-11-11T08:00:00+07:00"
  }
}
```

## 2. Update Current User

Endpoint : `PATCH /api/users/me`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Request Body :
```json
{
  "fullName": "John Updated",
  "timezone": "Asia/Jakarta"
}
```

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Profile updated successfully",
  "data": {
    "id": "USER_UUID",
    "fullName": "John Updated",
    "email": "john@example.com",
    "timezone": "Asia/Jakarta"
  }
}
```

Response Body (Failed, 422) :
```json
{
  "success": false,
  "message": "Validation failed",
  "errors": {
    "timezone": "Timezone is not a valid IANA zone id"
  }
}
```

Validasi:
- `fullName` 2–100 karakter dan tidak boleh kosong.
- `timezone` harus nama IANA valid.

Catatan:
- Semua field bersifat opsional pada PATCH (lihat semantik PATCH di konvensi).
- Email tidak diubah melalui endpoint ini.
- Jika `timezone` berubah, backend membatalkan occurrence `PENDING` mendatang lalu membuatnya ulang sesuai timezone baru. Log yang sudah `TAKEN`, `SKIPPED`, atau `MISSED` tidak diubah.

## 3. Change Password

Endpoint : `PATCH /api/users/me/password`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Request Body :
```json
{
  "currentPassword": "StrongPassword123",
  "newPassword": "NewStrongPassword456"
}
```

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Password changed successfully",
  "data": {
    "accessToken": "NEW_ACCESS_TOKEN",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "refreshToken": "NEW_REFRESH_TOKEN"
  }
}
```

Response Body (Failed, 422) :
```json
{
  "success": false,
  "message": "Current password is incorrect",
  "errors": {
    "currentPassword": "Password does not match"
  }
}
```

Validasi: `newPassword` 8–72 karakter dan harus berbeda dari password saat ini. Pesan error `newPassword` yang sama dengan password lama: `"New password must be different from the current password"`.

Aturan:
- Setelah password berubah, **seluruh refresh token lama dicabut** (sesi di perangkat lain keluar).
- Response berisi pasangan token baru agar perangkat yang sedang dipakai tetap login.
