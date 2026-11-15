package com.interval.apibackend.notification.push;

/**
 * SENT          : diterima layanan push.
 * FAILED        : gagal, perangkat tetap aktif (mis. layanan sedang bermasalah).
 * INVALID_TOKEN : token tidak akan pernah berhasil lagi (aplikasi dihapus, dst.); perangkat dinonaktifkan.
 */
public record PushResult(Outcome outcome, String error) {

    public enum Outcome { SENT, FAILED, INVALID_TOKEN }

    public static PushResult sent() {
        return new PushResult(Outcome.SENT, null);
    }

    public static PushResult failed(String error) {
        return new PushResult(Outcome.FAILED, error);
    }

    public static PushResult invalidToken(String error) {
        return new PushResult(Outcome.INVALID_TOKEN, error);
    }
}
