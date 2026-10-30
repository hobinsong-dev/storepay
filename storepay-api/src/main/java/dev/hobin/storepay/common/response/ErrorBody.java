package dev.hobin.storepay.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(String code, String message, List<FieldError> fieldErrors) {

    public static ErrorBody of(String code, String message) {
        return new ErrorBody(code, message, null);
    }

    public static ErrorBody of(String code, String message, List<FieldError> fieldErrors) {
        return new ErrorBody(code, message, fieldErrors.isEmpty() ? null : fieldErrors);
    }

    public record FieldError(String field, String reason) {
    }
}
