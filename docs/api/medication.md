# Medication API Spec

> Aturan umum (format response, status code, timezone, validasi) ada di [00-conventions.md](00-conventions.md).

Semua endpoint pada module ini memerlukan autentikasi. Pengguna hanya dapat mengakses obat miliknya sendiri; obat milik pengguna lain dan obat yang sudah dihapus dikembalikan sebagai `404`.

## Bentuk objek Medication

Semua endpoint (list, detail, create, update) mengembalikan objek dengan field yang sama:

| Field | Tipe | Keterangan |
|---|---|---|
| `id` | UUID | |
| `name` | string | Wajib, maksimal 150 karakter |
| `dosage` | string / null | Kekuatan obat, maksimal 100 karakter, contoh `500 mg` |
| `dosageForm` | enum | `TABLET`, `CAPSULE`, `SYRUP`, `DROPS`, `OTHER` (default `OTHER`) |
| `instructions` | string / null | Aturan pakai |
| `notes` | string / null | Catatan |
| `imageUrl` | string / null | Maksimal 500 karakter. Upload gambar di luar cakupan spec ini |
| `isActive` | boolean | `false` = obat dijeda, pengingat berhenti |
| `createdAt` | datetime | |
| `updatedAt` | datetime | |

## 1. Get Medication List

Endpoint : `GET /api/medications?page=0&size=10&active=true`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Query Parameters :
- `page` (optional, default 0)
- `size` (optional, default 10, maksimum 100)
- `active` (optional, filter `isActive`; jika kosong tampil semua yang belum dihapus)

Urutan: `createdAt` terbaru lebih dulu.

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Medication list retrieved successfully",
  "data": {
    "items": [
      {
        "id": "MEDICATION_UUID",
        "name": "Paracetamol",
        "dosage": "500 mg",
        "dosageForm": "TABLET",
        "instructions": "Diminum setelah makan",
        "notes": null,
        "imageUrl": null,
        "isActive": true,
        "createdAt": "2026-11-11T08:00:00+07:00",
        "updatedAt": "2026-11-11T08:00:00+07:00"
      }
    ],
    "page": 0,
    "size": 10,
    "totalItems": 1,
    "totalPages": 1
  }
}
```

## 2. Create Medication

Endpoint : `POST /api/medications`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Request Body :
```json
{
  "name": "Paracetamol",
  "dosage": "500 mg",
  "dosageForm": "TABLET",
  "instructions": "Diminum setelah makan",
  "notes": "Hindari penggunaan melebihi dosis anjuran",
  "imageUrl": null
}
```

Response Body (Success, 201) :
```json
{
  "success": true,
  "message": "Medication created successfully",
  "data": {
    "id": "MEDICATION_UUID",
    "name": "Paracetamol",
    "dosage": "500 mg",
    "dosageForm": "TABLET",
    "instructions": "Diminum setelah makan",
    "notes": "Hindari penggunaan melebihi dosis anjuran",
    "imageUrl": null,
    "isActive": true,
    "createdAt": "2026-11-11T08:00:00+07:00",
    "updatedAt": "2026-11-11T08:00:00+07:00"
  }
}
```

Response Body (Failed, 422) :
```json
{
  "success": false,
  "message": "Validation failed",
  "errors": {
    "name": "Medication name is required"
  }
}
```

Validasi: `name` wajib dan tidak boleh kosong, maksimal 150 karakter. `dosageForm` harus salah satu nilai enum (jika salah: `422` dengan `errors.dosageForm`). `userId` tidak dikirim dari client; backend mengambilnya dari token.

## 3. Get Medication Detail

Endpoint : `GET /api/medications/{medicationId}`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Response Body (Success, 200) :
```json
{
  "success": true,
  "message": "Medication retrieved successfully",
  "data": {
    "id": "MEDICATION_UUID",
    "name": "Paracetamol",
    "dosage": "500 mg",
    "dosageForm": "TABLET",
    "instructions": "Diminum setelah makan",
    "notes": null,
    "imageUrl": null,
    "isActive": true,
    "createdAt": "2026-11-11T08:00:00+07:00",
    "updatedAt": "2026-11-11T08:00:00+07:00"
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

## 4. Update Medication

Endpoint : `PATCH /api/medications/{medicationId}`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Request Body (semua field opsional) :
```json
{
  "name": "Paracetamol 500 mg",
  "instructions": "Diminum setelah makan",
  "isActive": true
}
```

Response Body (Success, 200) : objek Medication lengkap.
```json
{
  "success": true,
  "message": "Medication updated successfully",
  "data": {
    "id": "MEDICATION_UUID",
    "name": "Paracetamol 500 mg",
    "dosage": "500 mg",
    "dosageForm": "TABLET",
    "instructions": "Diminum setelah makan",
    "notes": null,
    "imageUrl": null,
    "isActive": true,
    "createdAt": "2026-11-11T08:00:00+07:00",
    "updatedAt": "2026-11-11T09:30:00+07:00"
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
  "message": "Validation failed",
  "errors": {
    "dosageForm": "Dosage form must be TABLET, CAPSULE, SYRUP, DROPS, or OTHER"
  }
}
```

Aturan:
- PATCH hanya mengubah field yang dikirim. Backend memeriksa kepemilikan lewat `userId` dari token.
- Mengubah `isActive` menjadi `false` menghentikan pengingat: occurrence `PENDING` mendatang milik obat ini dibatalkan (`CANCELLED`). Mengubah kembali menjadi `true` membuat occurrence mendatang lagi sesuai jadwal aktif.
- Mengubah `name` tidak mengubah riwayat yang sudah ada (log menyimpan nama obat saat kejadian).

## 5. Delete Medication

Endpoint : `DELETE /api/medications/{medicationId}`

Request Header :
- `Authorization: Bearer <accessToken>` (Mandatory)

Response Body (Success, 204) : Tidak ada response body.

Response Body (Failed, 404) :
```json
{
  "success": false,
  "message": "Medication not found",
  "errors": null
}
```

Aturan: penghapusan adalah **soft delete** (`deleted_at` diisi).
- Semua jadwal obat tersebut ikut dinonaktifkan dan di-soft-delete.
- Occurrence `PENDING` mendatang diubah menjadi `CANCELLED`.
- Riwayat konsumsi yang sudah ada tetap dipertahankan sehingga adherence historis tidak berubah.
- Obat yang sudah dihapus tidak muncul di list maupun detail.
