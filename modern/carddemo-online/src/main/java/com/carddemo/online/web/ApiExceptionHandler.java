package com.carddemo.online.web;

import com.carddemo.online.service.LegacyRuleException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps rule failures to {message, field, legacyParagraph}; message is the verbatim legacy text. */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(LegacyRuleException.class)
    ResponseEntity<Map<String, Object>> rule(LegacyRuleException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", e.getMessage());
        body.put("field", e.getField());
        body.put("legacyParagraph", e.getParagraph());
        return ResponseEntity.status(e.getStatus()).body(body);
    }
}
