package com.interval.apibackend.common.response;

import java.util.Map;

/**
 * Format response gagal: { "success": false, "message": "...", "errors": ... }.
 * "errors" berupa map field -> pesan, atau null jika tidak terkait field.
 */
public record ApiErrorResponse(boolean success, String message, Map<String, String> errors) {

    public static ApiErrorResponse of(String message, Map<String, String> errors) {
        return new ApiErrorResponse(false, message, errors);
    }

    public static ApiErrorResponse of(String message) {
        return new ApiErrorResponse(false, message, null);
    }
}
