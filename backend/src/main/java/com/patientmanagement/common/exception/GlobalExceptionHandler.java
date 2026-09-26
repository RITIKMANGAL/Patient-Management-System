package com.patientmanagement.common.exception;

import com.patientmanagement.ai.service.AiProviderResponseException;
import com.patientmanagement.ai.service.AiServiceUnavailableException;
import com.patientmanagement.common.response.ErrorResponse;
import com.patientmanagement.feedback.service.FeedbackAccessDeniedException;
import com.patientmanagement.prescription.access.service.PrescriptionAccessDeniedException;
import com.patientmanagement.prescription.service.PrescriptionPdfGenerationException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");

        return buildErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");

        return buildErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .orElse("Validation failed");
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessageException() {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(MethodArgumentTypeMismatchException exception) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid value for '" + exception.getName() + "'");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameterException(
            MissingServletRequestParameterException exception
    ) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Missing required parameter '" + exception.getParameterName() + "'");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupportedException() {
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method is not supported for this endpoint");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupportedException() {
        return buildErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Media type is not supported");
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleMissingResource() {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException exception) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequestException(InvalidRequestException exception) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResourceException(DuplicateResourceException exception) {
        return buildErrorResponse(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException() {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication failed");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException() {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access is denied");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException() {
        return buildErrorResponse(HttpStatus.CONFLICT, "Request conflicts with existing data");
    }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentWrite() {
        return buildErrorResponse(HttpStatus.CONFLICT, "Request conflicts with a concurrent change. Please retry.");
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAiServiceUnavailableException(AiServiceUnavailableException exception) {
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(AiProviderResponseException.class)
    public ResponseEntity<ErrorResponse> handleAiProviderResponseException(AiProviderResponseException exception) {
        return buildErrorResponse(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler(PrescriptionPdfGenerationException.class)
    public ResponseEntity<ErrorResponse> handlePrescriptionPdfGenerationException(
            PrescriptionPdfGenerationException exception
    ) {
        LOGGER.error("Prescription PDF generation failed", exception);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate prescription PDF");
    }

    @ExceptionHandler(PrescriptionAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handlePrescriptionAccessDeniedException() {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Prescription access link is invalid or expired");
    }

    @ExceptionHandler(FeedbackAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleFeedbackAccessDeniedException() {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Feedback link is invalid or expired");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        LOGGER.error("Unexpected API error", exception);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String message) {
        ErrorResponse response = new ErrorResponse(status.value(), status.getReasonPhrase(), message, Instant.now());
        return ResponseEntity.status(status).body(response);
    }
}
