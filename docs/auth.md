# Authentication API Spec

> Aturan umum (format response, status code, timezone, validasi) ada di [00-conventions.md](00-conventions.md).

Endpoint yang tidak memerlukan autentikasi: `register`, `login`, dan `refresh`.

## Ketentuan token

| | Access token | Refresh token |
|---|---|---|
| Bentuk | JWT (HS256) | String acak 256-bit, base64url |
| Masa berlaku | 15 menit (`expiresIn: 900`) | 30 hari |
| Isi / penyimpanan | Claim `sub` = userId, `iat`, `exp` | Disimpan sebagai **SHA-256 hex** pada `refresh_tokens.token_hash` |

Secret JWT dibaca dari environment variable, tidak boleh di-commit.

## 1. Register User

Endpoint : `POST /api/auth/register`

Request Body :
```json
{
  "fullName": "John Doe",
  "email": "john@example.com",
  "password": "StrongPassword123",
  "timezone": "Asia/Jakarta"
}
```

Response Body (Success, 201) :
```json
{
  "success": true,
  "message": "User registered successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "fullName": "John Doe",
    "email": "john@example.com",
    "timezone": "Asia/Jakarta"
  }
}
```

Response Body (Failed, 409) :
```json
{
  "success": false,
  "message": "Email is already registered",
  "errors": {
    "email": "Email already exists"
  }
}
```

Response Body (Failed, 422) :
```json
{
  "success": false,
  "message": "Validation failed",
  "errors": {
    "password": "Password must be between 8 and 72 characters"
  }
}
```

Validasi:
- `fullName` wajib, 2–100 karakter.
- `email` wajib, berformat email, maksimal 150 karakter, unik. Email di-trim dan dibandingkan tanpa membedakan huruf besar/kecil.
- `password` wajib, **8–72 karakter** (BCrypt hanya memproses 72 byte pertama).
- `timezone` opsional, harus nama IANA valid. Default `Asia/Jakarta`.

Aturan:
- Password di-hash dengan BCrypt sebelum disimpan.
- Backend membuat baris `notification_preferences` default untuk pengguna baru.
- Register tidak mengembalikan token; client memanggil login setelahnya.

## 2. Login

Endpoint : `POST /api/auth/login`

Request Body :
```json
{
  "email": "john@example.com",
  "password": "StrongPassword123"
}
```

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "ACCESS_TOKEN",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "refreshToken": "REFRESH_TOKEN",
    "user": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "fullName": "John Doe",
      "email": "john@example.com",
      "timezone": "Asia/Jakarta"
    }
  }
}
```

Response Body (Failed, 401) :
```json
{
  "success": false,
  "message": "Email or password is incorrect",
  "errors": null
}
```

Response Body (Failed, 403) :
```json
{
  "success": false,
  "message": "Account is deactivated",
  "errors": null
}
```

Response Body (Failed, 429) :
```json
{
  "success": false,
  "message": "Too many login attempts. Please try again later",
  "errors": null
}
```

Catatan:
- `expiresIn` dalam detik.
- Pesan `401` sama untuk email tidak ditemukan dan password salah (mencegah enumerasi akun).
- `403` hanya dikembalikan jika password benar tetapi `isActive = false`.
- Rate limiting login bersifat opsional pada tahap awal.

## 3. Refresh Token

Endpoint : `POST /api/auth/refresh`

Request Body :
```json
{
  "refreshToken": "REFRESH_TOKEN"
}
```

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Token refreshed successfully",
  "data": {
    "accessToken": "NEW_ACCESS_TOKEN",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "refreshToken": "NEW_REFRESH_TOKEN"
  }
}
```

Response Body (Failed, 401) :
```json
{
  "success": false,
  "message": "Refresh token is invalid or expired",
  "errors": null
}
```

Aturan:
- Rotasi wajib: token lama diberi `revoked_at` dan `replaced_by_token_id` menunjuk token baru.
- **Deteksi pemakaian ulang:** jika token yang sudah dicabut dipakai lagi, anggap token bocor. Cabut **semua** refresh token milik pengguna tersebut dan kembalikan `401`.
- Token ditolak jika sudah kedaluwarsa, dicabut, atau pemiliknya tidak aktif.

## 4. Logout

Endpoint : `POST /api/auth/logout`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Request Body :
```json
{
  "refreshToken": "REFRESH_TOKEN"
}
```

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Logout successful",
  "data": null
}
```

Aturan:
- Hanya refresh token milik pengguna yang sedang login yang dicabut.
- Idempotent: jika token sudah dicabut atau tidak dikenal, tetap `200`.
- Access token berumur pendek dan tidak di-blacklist; client wajib menghapusnya dari penyimpanan lokal.
- Sebelum logout, client sebaiknya memanggil `DELETE /api/devices/{deviceId}` agar perangkat tidak menerima notifikasi pengguna ini lagi.

## 5. Logout All Sessions

Endpoint : `POST /api/auth/logout-all`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Request Body : Tidak ada.

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "All sessions have been logged out",
  "data": null
}
```

Aturan: seluruh refresh token aktif milik pengguna ditandai revoked.
