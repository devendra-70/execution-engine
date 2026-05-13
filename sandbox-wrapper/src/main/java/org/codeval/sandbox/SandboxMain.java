package org.codeval.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone sandbox wrapper entry point.
 * Reads a JSON SandboxRequest from stdin, executes each test case
 * with a fresh ClassLoader (preventing static state-bleed), and writes
 * a JSON SandboxResponse to stdout.
 */
public class SandboxMain {

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Read full stdin as JSON
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        SandboxRequest request = mapper.readValue(sb.toString(), SandboxRequest.class);
        SandboxRunner runner = new SandboxRunner();

        List<TestCaseResult> results = runner.run(request);

        SandboxResponse response = new SandboxResponse(results);
        System.out.println(mapper.writeValueAsString(response));
    }
}

