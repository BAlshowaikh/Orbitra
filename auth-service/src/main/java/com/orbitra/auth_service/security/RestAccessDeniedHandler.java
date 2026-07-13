/*
  RestAccessDeniedHandler.java
  Runs when an authenticated request is rejected by an authorizeHttpRequests
  rule (e.g. hasRole("ADMIN")) - that AccessDeniedException is thrown inside
  Spring Security's filter chain, before the request ever reaches
  DispatcherServlet, so GlobalExceptionHandler's @RestControllerAdvice cannot
  see it. This handler exists so a wrong-role 403 still gets the same JSON
  ErrorResponse shape as every other error in the app, instead of Spring
  Security's blank default body.
*/
package com.orbitra.auth_service.security;

// ----------- IMPORTS -----------
import com.orbitra.auth_service.dto.ErrorResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    // JsonMapper (package tools.jackson.*, Jackson 3) is the actual bean type
    // Spring Boot 4's JacksonAutoConfiguration publishes - reusing it keeps JSON
    // serialization consistent with the rest of the app's responses. The
    // similarly-named com.fasterxml.jackson.databind.ObjectMapper (Jackson 2)
    // is a different class with no Spring-managed bean.
    public RestAccessDeniedHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException, ServletException {

        // Fixed message rather than ex.getMessage() - keeps the response
        // consistent with GlobalExceptionHandler's other deliberate messages,
        // and avoids leaking Spring Security's internal wording to callers.
        ErrorResponse body = new ErrorResponse(Instant.now(), HttpStatus.FORBIDDEN.value(), "Access denied");

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getWriter(), body);
    }
}
