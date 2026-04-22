package zac.job.interview.basicapp.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import zac.job.interview.basicapp.data.ApiError;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpHeaders;

import java.time.Instant;
import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiError> handleValidation(
                        MethodArgumentNotValidException ex,
                        HttpServletRequest request) {
                List<String> details = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(this::formatFieldError)
                                .toList();

                ApiError error = ApiError.builder()
                                .timestamp(Instant.now())
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error("Validation Failed")
                                .message("Invalid request body")
                                .details(details)
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.badRequest().body(error);
        }

        private String formatFieldError(FieldError error) {
                return error.getField() + ": " + error.getDefaultMessage();
        }

        /**
         * Example: IllegalArgumentException
         */
        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ApiError> handleBadRequest(
                        IllegalArgumentException ex,
                        HttpServletRequest request) {
                ApiError error = ApiError.builder()
                                .timestamp(Instant.now())
                                .status(HttpStatus.BAD_REQUEST.value())
                                .error("Bad Request")
                                .message(ex.getMessage())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.badRequest().body(error);
        }

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<?> handleNoResource(
                        NoResourceFoundException ex,
                        HttpServletRequest request) throws NoResourceFoundException {

                String accept = request.getHeader(HttpHeaders.ACCEPT);

                // 👉 If browser (HTML request) → let Spring handle it
                if (accept != null && accept.contains("text/html")) {
                        throw ex; // VERY IMPORTANT
                }

                // 👉 API → return JSON
                ApiError error = ApiError.builder()
                                .timestamp(Instant.now())
                                .status(HttpStatus.NOT_FOUND.value())
                                .error("Not Found")
                                .message("Resource not found: " + request.getRequestURI())
                                .path(request.getRequestURI())
                                .build();

                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<?> handleAll(
                        Exception ex,
                        HttpServletRequest request) throws Exception {
                String accept = request.getHeader("Accept");

                if (accept != null && accept.contains("text/html")) {
                        throw ex; // let Spring render error page
                }

                return ResponseEntity.status(500)
                .header("Location", "/error.html")
                .build();  // or use redirect view
        }

}