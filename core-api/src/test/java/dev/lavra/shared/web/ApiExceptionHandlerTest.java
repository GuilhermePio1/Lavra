package dev.lavra.shared.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * The advice on its own, driven through real MVC dispatch so the exception
 * under test is the one Spring actually throws — not one assembled by hand.
 * The endpoints below are fixtures: no production route takes a body yet.
 *
 * <p>Assertions never match the text of a constraint message, which the Bean
 * Validation default bundle localises to the JVM locale.
 */
class ApiExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FixtureController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("a field error becomes the message, prefixed by the field name")
    void reportsFieldError() throws Exception {
        mockMvc.perform(post("/fixture/episodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "   ", "durationSeconds": 30}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiError.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message").value(containsString("title: ")));
    }

    @Test
    @DisplayName("several field errors are joined, all of them reported at once")
    void joinsEveryFieldError() throws Exception {
        mockMvc.perform(post("/fixture/episodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "   ", "durationSeconds": 0}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiError.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message").value(containsString("title: ")))
                .andExpect(jsonPath("$.message").value(containsString("durationSeconds: ")))
                .andExpect(jsonPath("$.message").value(containsString("; ")));
    }

    /**
     * The case that makes the fallback earn its place: a constraint declared on
     * the type produces a global error and no field error at all, so there is
     * nothing to join. The contract requires a message either way.
     */
    @Test
    @DisplayName("a class-level constraint leaves no field error: the fallback message answers")
    void fallsBackWhenOnlyGlobalErrorsExist() throws Exception {
        mockMvc.perform(post("/fixture/chapters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startSeconds": 90, "endSeconds": 30}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ApiError.VALIDATION_ERROR))
                .andExpect(jsonPath("$.message").value("Invalid request"));
    }

    @RestController
    static class FixtureController {

        @PostMapping("/fixture/episodes")
        void createEpisode(@Valid @RequestBody EpisodeRequest request) {
        }

        @PostMapping("/fixture/chapters")
        void createChapter(@Valid @RequestBody ChapterRequest request) {
        }
    }

    record EpisodeRequest(@NotBlank String title, @Min(1) int durationSeconds) {
    }

    @OrderedRange
    record ChapterRequest(Integer startSeconds, Integer endSeconds) {
    }

    @Constraint(validatedBy = OrderedRangeValidator.class)
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface OrderedRange {

        String message() default "startSeconds must come before endSeconds";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    /** Public because Hibernate Validator instantiates it reflectively. */
    public static class OrderedRangeValidator implements ConstraintValidator<OrderedRange, ChapterRequest> {

        @Override
        public boolean isValid(ChapterRequest request, ConstraintValidatorContext context) {
            return request.startSeconds() == null
                    || request.endSeconds() == null
                    || request.startSeconds() < request.endSeconds();
        }
    }
}
