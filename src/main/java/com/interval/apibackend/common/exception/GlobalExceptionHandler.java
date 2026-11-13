package com.interval.apibackend.common.exception;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.FieldError;

import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.InvalidFormatException;
import com.interval.apibackend.common.response.ApiErrorResponse;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;

/**
 * Memetakan semua exception ke format error spec.
 *
 * Aturan (lihat 00-conventions.md):
 *  - 400: request tidak bisa dibaca (JSON rusak, tipe parameter/UUID salah)
 *  - 422: validasi field dan aturan bisnis
 *  - 500: pesan generik, detail hanya di log
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String VALIDATION_FAILED = "Validation failed";

    // ---------- exception bisnis ----------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        return build(ex.getStatus(), ex.getMessage(), ex.getErrors());
    }

    // ---------- validasi (422) ----------

    /** @Valid pada @RequestBody */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return build(HttpStatus.UNPROCESSABLE_ENTITY, VALIDATION_FAILED, errors);
    }

    /** Constraint pada parameter (@Min/@Max pada @RequestParam, dst.) - Spring 6.1+ */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String paramName = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String field = (error instanceof FieldError fe) ? fe.getField() : paramName;
                errors.putIfAbsent(field != null ? field : "request", error.getDefaultMessage());
            }
        });
        return build(HttpStatus.UNPROCESSABLE_ENTITY, VALIDATION_FAILED, errors);
    }

    /** Dipakai jika controller/service diberi @Validated. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.putIfAbsent(lastNode(violation.getPropertyPath()), violation.getMessage());
        }
        return build(HttpStatus.UNPROCESSABLE_ENTITY, VALIDATION_FAILED, errors);
    }

    // ---------- request tidak terbaca (400, kecuali nilai field salah = 422) ----------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        // Nilai field salah (enum tidak dikenal, format tanggal/jam salah) -> 422 per field.
        if (ex.getCause() instanceof InvalidFormatException ife) {
            return build(HttpStatus.UNPROCESSABLE_ENTITY, VALIDATION_FAILED,
                    Map.of(jsonPath(ife), invalidValueMessage(ife)));
        }
        // JSON rusak, body kosong, tipe struktur salah -> 400.
        return build(HttpStatus.BAD_REQUEST, "Malformed JSON request", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, "Required parameter '" + ex.getParameterName() + "' is missing", null);
    }

    // ---------- routing ----------

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Resource not found", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", null);
    }

    // ---------- Spring Security ----------
    // Exception dari FILTER ditangani AuthenticationEntryPoint / AccessDeniedHandler
    // (dibuat di tahap Auth). Handler ini hanya untuk yang dilempar dari dalam controller.

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "Access denied", null);
    }

    // ---------- fallback ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", null);
    }

    // ---------- helper ----------

    private static ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message,
                                                          Map<String, String> errors) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(message, errors));
    }

    private static String lastNode(Path path) {
        String last = "request";
        for (Path.Node node : path) {
            if (node.getName() != null) {
                last = node.getName();
            }
        }
        return last;
    }

    /** Enum salah -> "Value must be one of: TABLET, CAPSULE, ..."; selain itu pesan generik. */
    private static String invalidValueMessage(InvalidFormatException ex) {
        Class<?> target = ex.getTargetType();
        if (target != null && target.isEnum()) {
            String values = Arrays.stream(target.getEnumConstants())
                    .map(constant -> ((Enum<?>) constant).name())
                    .collect(Collectors.joining(", "));
            return "Value must be one of: " + values;
        }
        return "Invalid value";
    }

    /** Mengubah path Jackson menjadi "times[0].scheduledTime". */
    private static String jsonPath(DatabindException ex) {
        StringBuilder sb = new StringBuilder();
        for (DatabindException.Reference ref : ex.getPath()) {
            if (ref.getPropertyName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.isEmpty() ? "request" : sb.toString();
    }
}
