package org.codeval.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Model classes")
class ModelTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ──────────────────────────────────────────────────────────────────────────
    // TestCaseInput
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TestCaseInput")
    class TestCaseInputTests {

        @Test
        @DisplayName("all-args constructor sets all fields")
        void allArgsConstructor() {
            TestCaseInput tc = new TestCaseInput(7L, 3L, "stdin", "stdout", 2000);
            assertThat(tc.getId()).isEqualTo(7L);
            assertThat(tc.getProblemId()).isEqualTo(3L);
            assertThat(tc.getInput()).isEqualTo("stdin");
            assertThat(tc.getExpectedOutput()).isEqualTo("stdout");
            assertThat(tc.getTimeoutMs()).isEqualTo(2000);
        }

        @Test
        @DisplayName("no-args constructor produces empty instance")
        void noArgsConstructor() {
            TestCaseInput tc = new TestCaseInput();
            assertThat(tc.getId()).isNull();
            assertThat(tc.getInput()).isNull();
        }

        @Test
        @DisplayName("setters work correctly")
        void setters() {
            TestCaseInput tc = new TestCaseInput();
            tc.setId(5L);
            tc.setProblemId(10L);
            tc.setInput("hello");
            tc.setExpectedOutput("world");
            tc.setTimeoutMs(1000);

            assertThat(tc.getId()).isEqualTo(5L);
            assertThat(tc.getProblemId()).isEqualTo(10L);
            assertThat(tc.getInput()).isEqualTo("hello");
            assertThat(tc.getExpectedOutput()).isEqualTo("world");
            assertThat(tc.getTimeoutMs()).isEqualTo(1000);
        }

        @Test
        @DisplayName("JSON round-trip preserves all fields")
        void jsonRoundTrip() throws Exception {
            TestCaseInput original = new TestCaseInput(1L, 2L, "in", "out", 500);
            String json = MAPPER.writeValueAsString(original);
            TestCaseInput restored = MAPPER.readValue(json, TestCaseInput.class);

            assertThat(restored.getId()).isEqualTo(1L);
            assertThat(restored.getProblemId()).isEqualTo(2L);
            assertThat(restored.getInput()).isEqualTo("in");
            assertThat(restored.getExpectedOutput()).isEqualTo("out");
            assertThat(restored.getTimeoutMs()).isEqualTo(500);
        }

        @Test
        @DisplayName("unknown JSON fields are ignored")
        void ignoresUnknownFields() throws Exception {
            String json = "{\"id\":1,\"unknownField\":\"value\",\"input\":\"x\"}";
            assertThatCode(() -> MAPPER.readValue(json, TestCaseInput.class))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("equals and hashCode based on all fields")
        void equalsAndHashCode() {
            TestCaseInput a = new TestCaseInput(1L, 2L, "i", "o", 500);
            TestCaseInput b = new TestCaseInput(1L, 2L, "i", "o", 500);
            TestCaseInput c = new TestCaseInput(2L, 2L, "i", "o", 500);

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
            assertThat(a).isNotEqualTo(c);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // TestCaseResult
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TestCaseResult")
    class TestCaseResultTests {

        @Test
        @DisplayName("builder sets all fields")
        void builder() {
            TestCaseResult r = TestCaseResult.builder()
                    .testCaseId(10L)
                    .verdict("ACCEPTED")
                    .actualOutput("42")
                    .expectedOutput("42")
                    .runtimeMs(123L)
                    .memoryBytes(4096L)
                    .errorMessage(null)
                    .build();

            assertThat(r.getTestCaseId()).isEqualTo(10L);
            assertThat(r.getVerdict()).isEqualTo("ACCEPTED");
            assertThat(r.getActualOutput()).isEqualTo("42");
            assertThat(r.getExpectedOutput()).isEqualTo("42");
            assertThat(r.getRuntimeMs()).isEqualTo(123L);
            assertThat(r.getMemoryBytes()).isEqualTo(4096L);
            assertThat(r.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("no-args constructor produces zeroed instance")
        void noArgsConstructor() {
            TestCaseResult r = new TestCaseResult();
            assertThat(r.getVerdict()).isNull();
            assertThat(r.getRuntimeMs()).isZero();
        }

        @Test
        @DisplayName("all-args constructor sets all fields")
        void allArgsConstructor() {
            TestCaseResult r = new TestCaseResult(1L, "WRONG_ANSWER", "x", "y", 55L, 2048L, "err");
            assertThat(r.getVerdict()).isEqualTo("WRONG_ANSWER");
            assertThat(r.getErrorMessage()).isEqualTo("err");
        }

        @Test
        @DisplayName("JSON serialisation includes all populated fields")
        void jsonSerialisation() throws Exception {
            TestCaseResult r = TestCaseResult.builder()
                    .testCaseId(1L)
                    .verdict("COMPILE_ERROR")
                    .errorMessage("Line 1: error")
                    .build();

            String json = MAPPER.writeValueAsString(r);
            assertThat(json).contains("COMPILE_ERROR").contains("Line 1: error");
        }

        @Test
        @DisplayName("equals and hashCode based on all fields")
        void equalsAndHashCode() {
            TestCaseResult a = TestCaseResult.builder().testCaseId(1L).verdict("ACCEPTED").build();
            TestCaseResult b = TestCaseResult.builder().testCaseId(1L).verdict("ACCEPTED").build();
            TestCaseResult c = TestCaseResult.builder().testCaseId(2L).verdict("ACCEPTED").build();

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
            assertThat(a).isNotEqualTo(c);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SandboxRequest
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SandboxRequest")
    class SandboxRequestTests {

        @Test
        @DisplayName("all-args constructor sets all fields")
        void allArgsConstructor() {
            List<TestCaseInput> cases = List.of(new TestCaseInput(1L, 1L, "i", "o", 0));
            SandboxRequest req = new SandboxRequest("code", cases, 3000L);

            assertThat(req.getSourceCode()).isEqualTo("code");
            assertThat(req.getTestCases()).hasSize(1);
            assertThat(req.getTimeoutMs()).isEqualTo(3000L);
        }

        @Test
        @DisplayName("no-args constructor produces empty instance")
        void noArgsConstructor() {
            SandboxRequest req = new SandboxRequest();
            assertThat(req.getSourceCode()).isNull();
            assertThat(req.getTestCases()).isNull();
        }

        @Test
        @DisplayName("JSON round-trip preserves fields")
        void jsonRoundTrip() throws Exception {
            List<TestCaseInput> cases = List.of(new TestCaseInput(1L, 1L, "in", "out", 0));
            SandboxRequest original = new SandboxRequest("public class Solution {}", cases, 2000L);

            String json = MAPPER.writeValueAsString(original);
            SandboxRequest restored = MAPPER.readValue(json, SandboxRequest.class);

            assertThat(restored.getSourceCode()).isEqualTo("public class Solution {}");
            assertThat(restored.getTestCases()).hasSize(1);
            assertThat(restored.getTimeoutMs()).isEqualTo(2000L);
        }

        @Test
        @DisplayName("unknown JSON fields are ignored")
        void ignoresUnknownFields() throws Exception {
            String json = "{\"sourceCode\":\"x\",\"extra\":\"ignored\",\"timeoutMs\":1000}";
            assertThatCode(() -> MAPPER.readValue(json, SandboxRequest.class))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("setters work")
        void setters() {
            SandboxRequest req = new SandboxRequest();
            req.setSourceCode("src");
            req.setTimeoutMs(999L);
            req.setTestCases(List.of());

            assertThat(req.getSourceCode()).isEqualTo("src");
            assertThat(req.getTimeoutMs()).isEqualTo(999L);
            assertThat(req.getTestCases()).isEmpty();
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SandboxResponse
    // ──────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SandboxResponse")
    class SandboxResponseTests {

        @Test
        @DisplayName("all-args constructor sets results list")
        void allArgsConstructor() {
            List<TestCaseResult> results = List.of(
                    TestCaseResult.builder().testCaseId(1L).verdict("ACCEPTED").build());
            SandboxResponse resp = new SandboxResponse(results);
            assertThat(resp.getResults()).hasSize(1);
        }

        @Test
        @DisplayName("no-args constructor produces null results")
        void noArgsConstructor() {
            SandboxResponse resp = new SandboxResponse();
            assertThat(resp.getResults()).isNull();
        }

        @Test
        @DisplayName("setResults and getResults work")
        void setterGetter() {
            SandboxResponse resp = new SandboxResponse();
            List<TestCaseResult> results = List.of(
                    TestCaseResult.builder().testCaseId(2L).verdict("WRONG_ANSWER").build());
            resp.setResults(results);
            assertThat(resp.getResults().get(0).getVerdict()).isEqualTo("WRONG_ANSWER");
        }

        @Test
        @DisplayName("JSON round-trip preserves results list")
        void jsonRoundTrip() throws Exception {
            List<TestCaseResult> results = List.of(
                    TestCaseResult.builder().testCaseId(1L).verdict("ACCEPTED").actualOutput("42").build());
            SandboxResponse original = new SandboxResponse(results);

            String json = MAPPER.writeValueAsString(original);
            SandboxResponse restored = MAPPER.readValue(json, SandboxResponse.class);

            assertThat(restored.getResults()).hasSize(1);
            assertThat(restored.getResults().get(0).getVerdict()).isEqualTo("ACCEPTED");
        }

        @Test
        @DisplayName("empty results list serialises cleanly")
        void emptyResults() throws Exception {
            SandboxResponse resp = new SandboxResponse(List.of());
            String json = MAPPER.writeValueAsString(resp);
            assertThat(json).contains("results").contains("[]");
        }
    }
}
