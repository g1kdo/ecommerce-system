package rw.smart.ecommerce.utils.response;

import org.springframework.http.HttpStatus;

/**
 * The single response envelope every REST endpoint returns, success or failure.
 * A client parses one shape and reads {@code status} to branch, instead of
 * guessing whether a body is a payload or an error.
 */
public record StandardResponse<T>(int status, String message, T data) {

    public static <T> StandardResponse<T> of(HttpStatus status, String message, T data) {
        return new StandardResponse<>(status.value(), message, data);
    }

    public static <T> StandardResponse<T> ok(String message, T data) {
        return of(HttpStatus.OK, message, data);
    }

    public static <T> StandardResponse<T> created(String message, T data) {
        return of(HttpStatus.CREATED, message, data);
    }

    /** Errors carry no payload unless the handler supplies field details. */
    public static <T> StandardResponse<T> error(HttpStatus status, String message) {
        return new StandardResponse<>(status.value(), message, null);
    }

    public static <T> StandardResponse<T> error(HttpStatus status, String message, T data) {
        return of(status, message, data);
    }
}
