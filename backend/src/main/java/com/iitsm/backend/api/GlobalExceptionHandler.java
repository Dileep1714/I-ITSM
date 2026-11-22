package com.iitsm.backend.api;

import com.iitsm.backend.api.dto.ErrorResponse;
import com.iitsm.backend.common.exception.ItsmException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Clock;
import java.util.List;

/**
 * Turns every exception into the same {@link ErrorResponse} JSON shape, with the right HTTP status.
 *
 * <p>Application exceptions declare their own status ({@link ItsmException#httpStatus()}), so one handler covers
 * all of them: adding a new exception type needs no change here. Unexpected errors are logged in full but
 * return a generic message, so internal details (SQL, stack traces) never leak to the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ItsmException.class)
    public ResponseEntity<ErrorResponse> handleApplication(ItsmException e, HttpServletRequest request) {
        return respond(e.httpStatus(), e.getCode(), e.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e,
                                                          HttpServletRequest request) {
        List<ErrorResponse.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                // A binding failure (e.g. "status=FOO" in a query string) carries Spring's technical conversion
                // message; replace it with a readable one.
                .map(error -> new ErrorResponse.FieldError(error.getField(),
                        error.isBindingFailure() ? "has an invalid value" : error.getDefaultMessage()))
                .toList();
        return respond(400, "INVALID_INPUT", "Some fields are missing or invalid", request, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e,
                                                          HttpServletRequest request) {
        return respond(400, "MALFORMED_REQUEST", "Request body is not valid JSON or contains an unknown value",
                request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                            HttpServletRequest request) {
        return respond(400, "INVALID_INPUT", "Parameter '" + e.getName() + "' has an invalid value", request,
                List.of());
    }

    /** Two people edited the same incident at once (optimistic locking): the later save is refused. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentUpdate(ObjectOptimisticLockingFailureException e,
                                                                HttpServletRequest request) {
        return respond(409, "CONCURRENT_UPDATE",
                "Someone else changed this record at the same time. Reload and try again.", request, List.of());
    }

    /** A database constraint caught something the application didn't, e.g. a duplicate email in a race. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException e,
                                                         HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), e.getMostSpecificCause().getMessage());
        return respond(409, "DATA_CONFLICT", "The request conflicts with existing data", request, List.of());
    }

    /** Database down or unreachable (the spec's "database failure" case). */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleDatabase(DataAccessException e, HttpServletRequest request) {
        log.error("Database error on {}", request.getRequestURI(), e);
        return respond(503, "DATABASE_UNAVAILABLE", "The database is temporarily unavailable. Please try again.",
                request, List.of());
    }

    /** From @PreAuthorize checks inside the application (URL-level denials are handled by the security layer). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        return respond(403, "FORBIDDEN", "You do not have permission to perform this action", request, List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return respond(413, "FILE_TOO_LARGE", "A photo can be at most 5 MB", request, List.of());
    }

    /** Upload requests without the expected file part, or not multipart at all. */
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadUpload(Exception e, HttpServletRequest request) {
        return respond(400, "INVALID_INPUT", "Send the photo as multipart/form-data in a field named 'file'", request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
        return respond(404, "NOT_FOUND", "No such endpoint", request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e,
                                                      HttpServletRequest request) {
        return respond(405, "METHOD_NOT_ALLOWED", e.getMessage(), request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), e);
        return respond(500, "INTERNAL_ERROR", "Something went wrong on our side. The error has been logged.",
                request, List.of());
    }

    private ResponseEntity<ErrorResponse> respond(int status, String code, String message,
                                                  HttpServletRequest request,
                                                  List<ErrorResponse.FieldError> fieldErrors) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message, status,
                request.getRequestURI(), clock.instant(), fieldErrors));
    }
}
