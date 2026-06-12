package io.mehdieidi.modless.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    @Test
    void missingStaticResourceMapsToNotFound() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.missingResource(new NoResourceFoundException(HttpMethod.POST,
                "api/chatbot/sessions/messages"));

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Resource not found.", response.getBody().message());
    }
}
