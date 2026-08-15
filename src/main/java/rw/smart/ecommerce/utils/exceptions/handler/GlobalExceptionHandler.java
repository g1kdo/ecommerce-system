package rw.smart.ecommerce.utils.exceptions.handler;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import rw.smart.ecommerce.utils.response.StandardResponse;
import rw.smart.ecommerce.utils.exceptions.DocumentStoreException;
import rw.smart.ecommerce.utils.exceptions.DuplicateResourceException;
import rw.smart.ecommerce.utils.exceptions.InsufficientStockException;
import rw.smart.ecommerce.utils.exceptions.InvalidInputException;
import rw.smart.ecommerce.utils.exceptions.ResourceNotFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single place where an exception becomes an HTTP response.
 *
 * Every branch returns the same {@link StandardResponse} envelope as a
 * successful call, so a client parses one shape whatever happens. Two rules run
 * through all of it: the status must describe what actually went wrong (a
 * conflict is not a 500), and an internal failure must not leak a stack trace or
 * a driver message to the caller.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Bean Validation failures on {@code @Valid @RequestBody}. The field errors
     * are returned as {@code data} so a form can highlight the offending inputs
     * instead of showing one generic message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StandardResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException exception) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        exception.getBindingResult().getGlobalErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        return ResponseEntity.badRequest().body(StandardResponse.error(
                HttpStatus.BAD_REQUEST, "Validation failed for " + fieldErrors.size() + " field(s)", fieldErrors));
    }

    /** Constraint violations raised outside request-body binding. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<StandardResponse<Map<String, String>>> handleConstraintViolation(
            ConstraintViolationException exception) {

        Map<String, String> violations = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                violations.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));

        return ResponseEntity.badRequest().body(StandardResponse.error(
                HttpStatus.BAD_REQUEST, "Validation failed", violations));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<StandardResponse<Void>> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(StandardResponse.error(HttpStatus.NOT_FOUND, exception.getMessage()));
    }

    /** A uniqueness rule or a referential guard blocked the write. */
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<StandardResponse<Void>> handleDuplicate(DuplicateResourceException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(StandardResponse.error(HttpStatus.CONFLICT, exception.getMessage()));
    }

    /**
     * 409, not 400: the request was well formed and would have been valid a
     * moment earlier — the stock simply is not there now.
     */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<StandardResponse<Void>> handleInsufficientStock(InsufficientStockException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(StandardResponse.error(HttpStatus.CONFLICT, exception.getMessage()));
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<StandardResponse<Void>> handleInvalidInput(InvalidInputException exception) {
        return ResponseEntity.badRequest()
                .body(StandardResponse.error(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    /**
     * 502: the request was fine, a dependency this service relies on was not.
     * Reported distinctly so the caller knows a retry may succeed.
     */
    @ExceptionHandler(DocumentStoreException.class)
    public ResponseEntity<StandardResponse<Void>> handleDocumentStore(DocumentStoreException exception) {
        log.error("Document store failure", exception);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(StandardResponse.error(HttpStatus.BAD_GATEWAY, exception.getMessage()));
    }

    /** e.g. {@code ?categoryId=abc} where a Long was expected. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<StandardResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String expected = exception.getRequiredType() == null
                ? "the expected type"
                : exception.getRequiredType().getSimpleName();

        return ResponseEntity.badRequest().body(StandardResponse.error(HttpStatus.BAD_REQUEST,
                "Parameter '" + exception.getName() + "' must be a valid " + expected));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<StandardResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException exception) {

        return ResponseEntity.badRequest().body(StandardResponse.error(
                HttpStatus.BAD_REQUEST, "Required parameter '" + exception.getParameterName() + "' is missing"));
    }

    /** Malformed JSON, or an enum value outside the allowed set. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<StandardResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        log.debug("Unreadable request body", exception);
        return ResponseEntity.badRequest().body(StandardResponse.error(
                HttpStatus.BAD_REQUEST, "Request body is malformed or contains an unsupported value"));
    }

    /**
     * Authorisation failure from {@code @PreAuthorize}.
     *
     * This handler has to exist. Method security throws inside the controller
     * invocation, so the exception reaches this advice before Spring Security's
     * filter can translate it — and the catch-all below would report a refusal
     * to act as a server fault, telling the caller to retry something that will
     * never succeed.
     *
     * The message says nothing about what the resource is or whether it exists;
     * a 403 that describes its target is a discovery tool.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<StandardResponse<Void>> handleAccessDenied(AccessDeniedException exception) {
        log.warn("Access denied: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(StandardResponse.error(
                HttpStatus.FORBIDDEN, "You do not have permission to perform this action."));
    }

    /** Bad or missing credentials — distinct from "signed in, but not allowed". */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<StandardResponse<Void>> handleAuthentication(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(StandardResponse.error(
                HttpStatus.UNAUTHORIZED, "Authentication required."));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<StandardResponse<Void>> handleNoHandler(NoHandlerFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(StandardResponse.error(
                HttpStatus.NOT_FOUND, "No endpoint " + exception.getHttpMethod() + " " + exception.getRequestURL()));
    }

    /**
     * Last resort. The detail goes to the log, never to the response — an
     * unexpected failure must not become a description of the internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(StandardResponse.error(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later."));
    }
}
