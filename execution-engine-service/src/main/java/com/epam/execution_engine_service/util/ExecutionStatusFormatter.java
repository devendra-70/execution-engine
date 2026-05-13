package com.epam.execution_engine_service.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * ExecutionStatusFormatter — Formats execution status for API responses
 * Created for coverage improvement feature (EPMICMPCOD-352 Loop Run 2)
 * 
 * Formats:
 * - Status to human-readable string
 * - Status to emoji icon
 * - Status to HTTP code
 */
@Slf4j
@Component
public class ExecutionStatusFormatter {

    /**
     * Format status to human-readable string
     * @param status Status value (PENDING, RUNNING, COMPLETED, FAILED)
     * @return Formatted string
     */
    public String formatStatus(String status) {
        if (status == null || status.isBlank()) {
            return "Unknown Status";
        }
        
        return switch (status.toUpperCase()) {
            case "PENDING" -> "Execution Pending";
            case "RUNNING" -> "Execution Running";
            case "COMPLETED" -> "Execution Completed";
            case "FAILED" -> "Execution Failed";
            default -> "Unknown Status";
        };
    }

    /**
     * Get emoji icon for status
     * @param status Status value
     * @return Emoji string
     */
    public String getStatusEmoji(String status) {
        if (status == null || status.isBlank()) {
            return "❓";
        }
        
        return switch (status.toUpperCase()) {
            case "PENDING" -> "⏳";
            case "RUNNING" -> "▶️";
            case "COMPLETED" -> "✅";
            case "FAILED" -> "❌";
            default -> "❓";
        };
    }

    /**
     * Get HTTP status code for execution status
     * @param status Status value
     * @return HTTP status code (200, 202, 500)
     */
    public int getHttpStatusCode(String status) {
        if (status == null || status.isBlank()) {
            return 500;
        }
        
        return switch (status.toUpperCase()) {
            case "PENDING", "RUNNING" -> 202; // Accepted - processing
            case "COMPLETED" -> 200; // OK
            case "FAILED" -> 500; // Server error
            default -> 500;
        };
    }

    /**
     * Check if status is terminal (no further changes)
     * @param status Status value
     * @return true if COMPLETED or FAILED
     */
    public boolean isTerminal(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        
        return status.equals("COMPLETED") || status.equals("FAILED");
    }

    /**
     * Get next possible status from current status
     * @param currentStatus Current status
     * @return Next status or null if terminal
     */
    public String getNextStatus(String currentStatus) {
        if (currentStatus == null || currentStatus.isBlank()) {
            return "PENDING";
        }
        
        return switch (currentStatus.toUpperCase()) {
            case "PENDING" -> "RUNNING";
            case "RUNNING" -> "COMPLETED";
            case "COMPLETED", "FAILED" -> null; // Terminal states
            default -> null;
        };
    }
}
