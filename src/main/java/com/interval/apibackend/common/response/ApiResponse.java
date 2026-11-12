package com.interval.apibackend.common.response;

/**
 * Format response sukses: { "success": true, "message": "...", "data": ... }.
 * Field "data" selalu ikut diserialisasi (bernilai null untuk logout, dsb.),
 * sesuai spec.
 */
public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(true, message, null);
    }
}
