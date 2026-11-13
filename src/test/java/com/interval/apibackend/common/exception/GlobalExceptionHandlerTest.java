package com.interval.apibackend.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.interval.apibackend.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Test ringan tanpa Spring context, tanpa database, tanpa Security. */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFoundException_returns404() throws Exception {
        mvc.perform(get("/t/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Medication not found"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void businessRule_returns422WithFieldErrors() throws Exception {
        mvc.perform(get("/t/rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Invalid schedule configuration"))
                .andExpect(jsonPath("$.errors.times").value("At least one time is required"));
    }

    @Test
    void conflict_returns409() throws Exception {
        mvc.perform(get("/t/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.email").value("Email already exists"));
    }

    @Test
    void bodyValidation_returns422() throws Exception {
        mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").value("Name is required"));
    }

    @Test
    void invalidEnumValue_returns422() throws Exception {
        mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"color\":\"PURPLE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.color").value("Value must be one of: RED, BLUE"));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));
    }

    @Test
    void invalidUuidInPath_returns400() throws Exception {
        mvc.perform(get("/t/uuid/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for parameter 'id'"));
    }

    @Test
    void wrongHttpMethod_returns405() throws Exception {
        mvc.perform(post("/t/not-found"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unexpectedException_returns500WithoutLeakingDetails() throws Exception {
        mvc.perform(get("/t/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Internal server error"))
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void successResponse_keepsNullDataField() throws Exception {
        mvc.perform(post("/t/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @RestController
    @RequestMapping("/t")
    public static class TestController {

        public enum Color { RED, BLUE }

        public record Body(@NotBlank(message = "Name is required") String name, Color color) { }

        @PostMapping("/body")
        ApiResponse<Void> body(@Valid @RequestBody Body body) {
            return ApiResponse.success("ok");
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new NotFoundException("Medication not found");
        }

        @GetMapping("/rule")
        void rule() {
            throw new BusinessRuleException("Invalid schedule configuration", "times", "At least one time is required");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("Email is already registered", "email", "Email already exists");
        }

        @GetMapping("/uuid/{id}")
        ApiResponse<Void> uuid(@PathVariable UUID id) {
            return ApiResponse.success("ok");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}