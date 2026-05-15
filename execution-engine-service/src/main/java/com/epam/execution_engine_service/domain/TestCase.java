package com.epam.execution_engine_service.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCase {
    private Long id;
    private Long problemId;
    private String input;
    private String expectedOutput;
    private int timeoutMs;
}

