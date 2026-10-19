package com.accountledger.fixeddepositservice.exception;

import com.accountledger.fixeddepositservice.dto.ApiResponse;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(FixedDepositNotFoundException.class)
  public ResponseEntity<ApiResponse<Object>> handleNotFound(FixedDepositNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(PrematureWithdrawalException.class)
  public ResponseEntity<ApiResponse<Object>> handlePrematureWithdrawal(
      PrematureWithdrawalException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Object>> handleValidationErrors(
      MethodArgumentNotValidException ex) {
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            error -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              errors.put(fieldName, errorMessage);
            });
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("Validation failed: " + errors));
  }

  @ExceptionHandler(ClientNotFoundException.class)
  public ResponseEntity<ApiResponse<Object>> handleClientNotFound(ClientNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(ClientNotActiveException.class)
  public ResponseEntity<ApiResponse<Object>> handleClientNotActive(ClientNotActiveException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(ClientServiceUnavailableException.class)
  public ResponseEntity<ApiResponse<Object>> handleClientServiceUnavailable(
      ClientServiceUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(InvalidPayoutAccountException.class)
  public ResponseEntity<ApiResponse<Object>> handleInvalidPayoutAccount(
      InvalidPayoutAccountException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(SavingsServiceUnavailableException.class)
  public ResponseEntity<ApiResponse<Object>> handleSavingsServiceUnavailable(
      SavingsServiceUnavailableException ex) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(FixedDepositNotActiveException.class)
  public ResponseEntity<ApiResponse<Object>> handleDepositNotActive(
      FixedDepositNotActiveException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
  }

  /*
   * The row we needed stayed locked by another request for longer than the
   * lock timeout. Nothing was changed, so trying again is safe. A 503 (not a
   * 4xx) so that services calling us treat it as temporary and retry.
   */
  @ExceptionHandler(PessimisticLockingFailureException.class)
  public ResponseEntity<ApiResponse<Object>> handleLockTimeout(
      PessimisticLockingFailureException ex) {
    log.warn("Gave up waiting for a row lock: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(
            ApiResponse.error("The fixed deposit is busy with another request. Please try again."));
  }

  @ExceptionHandler(LedgerPostingNotFoundException.class)
  public ResponseEntity<ApiResponse<Object>> handleLedgerPostingNotFound(
      LedgerPostingNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(NotRetryableException.class)
  public ResponseEntity<ApiResponse<Object>> handleNotRetryable(NotRetryableException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(ex.getMessage()));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Object>> handleUnreadableBody(
      HttpMessageNotReadableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("Request body is missing or malformed"));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResponse.error("Invalid value for parameter: " + ex.getName()));
  }

  /*
   * Safety net for anything not handled above. Spring's own web errors
   * (unknown URL, unsupported method, ...) keep their real status code.
   * Everything else is logged and returned as a plain 500 so internal
   * details never reach the caller.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
    if (ex instanceof ErrorResponse errorResponse) {
      return ResponseEntity.status(errorResponse.getStatusCode())
          .body(ApiResponse.error(errorResponse.getBody().getDetail()));
    }
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("An unexpected error occurred"));
  }
}
