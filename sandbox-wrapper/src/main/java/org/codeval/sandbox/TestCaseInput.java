package org.codeval.sandbox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TestCaseInput {
    private Long id;
    private Long problemId;
    private String input;
    private String expectedOutput;
    private int timeoutMs;
}

