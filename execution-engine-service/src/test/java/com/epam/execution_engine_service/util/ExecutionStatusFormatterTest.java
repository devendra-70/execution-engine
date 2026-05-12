package com.epam.execution_engine_service.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExecutionStatusFormatterTest — 100% coverage test suite
 * Created for coverage improvement feature (EPMICMPCOD-352 Loop Run 2)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionStatusFormatter - 100% Coverage Tests")
class ExecutionStatusFormatterTest {

    @InjectMocks
    private ExecutionStatusFormatter formatter;

    @Nested
    @DisplayName("formatStatus() - Status Formatting")
    class FormatStatusTests {

        @Test
        @DisplayName("Should format PENDING status correctly")
        void testFormatStatus_Pending() {
            assertEquals("Execution Pending", formatter.formatStatus("PENDING"));
        }

        @Test
        @DisplayName("Should format pending status (lowercase) correctly")
        void testFormatStatus_Pending_Lowercase() {
            assertEquals("Execution Pending", formatter.formatStatus("pending"));
        }

        @Test
        @DisplayName("Should format RUNNING status correctly")
        void testFormatStatus_Running() {
            assertEquals("Execution Running", formatter.formatStatus("RUNNING"));
        }

        @Test
        @DisplayName("Should format running status (lowercase) correctly")
        void testFormatStatus_Running_Lowercase() {
            assertEquals("Execution Running", formatter.formatStatus("running"));
        }

        @Test
        @DisplayName("Should format COMPLETED status correctly")
        void testFormatStatus_Completed() {
            assertEquals("Execution Completed", formatter.formatStatus("COMPLETED"));
        }

        @Test
        @DisplayName("Should format completed status (lowercase) correctly")
        void testFormatStatus_Completed_Lowercase() {
            assertEquals("Execution Completed", formatter.formatStatus("completed"));
        }

        @Test
        @DisplayName("Should format FAILED status correctly")
        void testFormatStatus_Failed() {
            assertEquals("Execution Failed", formatter.formatStatus("FAILED"));
        }

        @Test
        @DisplayName("Should format failed status (lowercase) correctly")
        void testFormatStatus_Failed_Lowercase() {
            assertEquals("Execution Failed", formatter.formatStatus("failed"));
        }

        @Test
        @DisplayName("Should return default for unknown status")
        void testFormatStatus_Unknown() {
            assertEquals("Unknown Status", formatter.formatStatus("UNKNOWN_STATUS"));
        }

        @Test
        @DisplayName("Should return default for null status")
        void testFormatStatus_Null() {
            assertEquals("Unknown Status", formatter.formatStatus(null));
        }

        @Test
        @DisplayName("Should return default for blank status")
        void testFormatStatus_Blank() {
            assertEquals("Unknown Status", formatter.formatStatus("   "));
        }

        @Test
        @DisplayName("Should return default for empty status")
        void testFormatStatus_Empty() {
            assertEquals("Unknown Status", formatter.formatStatus(""));
        }
    }

    @Nested
    @DisplayName("getStatusEmoji() - Emoji Retrieval")
    class GetStatusEmojiTests {

        @Test
        @DisplayName("Should return hourglass emoji for PENDING")
        void testGetStatusEmoji_Pending() {
            assertEquals("⏳", formatter.getStatusEmoji("PENDING"));
        }

        @Test
        @DisplayName("Should return play emoji for RUNNING")
        void testGetStatusEmoji_Running() {
            assertEquals("▶️", formatter.getStatusEmoji("RUNNING"));
        }

        @Test
        @DisplayName("Should return checkmark emoji for COMPLETED")
        void testGetStatusEmoji_Completed() {
            assertEquals("✅", formatter.getStatusEmoji("COMPLETED"));
        }

        @Test
        @DisplayName("Should return X emoji for FAILED")
        void testGetStatusEmoji_Failed() {
            assertEquals("❌", formatter.getStatusEmoji("FAILED"));
        }

        @Test
        @DisplayName("Should return question mark emoji for unknown status")
        void testGetStatusEmoji_Unknown() {
            assertEquals("❓", formatter.getStatusEmoji("UNKNOWN_STATUS"));
        }

        @Test
        @DisplayName("Should return question mark emoji for null status")
        void testGetStatusEmoji_Null() {
            assertEquals("❓", formatter.getStatusEmoji(null));
        }

        @Test
        @DisplayName("Should return question mark emoji for blank status")
        void testGetStatusEmoji_Blank() {
            assertEquals("❓", formatter.getStatusEmoji("   "));
        }

        @Test
        @DisplayName("Should return question mark emoji for empty status")
        void testGetStatusEmoji_Empty() {
            assertEquals("❓", formatter.getStatusEmoji(""));
        }

        @Test
        @DisplayName("Should handle mixed case PENDING")
        void testGetStatusEmoji_PendingMixedCase() {
            assertEquals("⏳", formatter.getStatusEmoji("PeNdInG"));
        }
    }

    @Nested
    @DisplayName("getHttpStatusCode() - HTTP Status Code")
    class GetHttpStatusCodeTests {

        @Test
        @DisplayName("Should return 202 for PENDING status")
        void testGetHttpStatusCode_Pending() {
            assertEquals(202, formatter.getHttpStatusCode("PENDING"));
        }

        @Test
        @DisplayName("Should return 202 for RUNNING status")
        void testGetHttpStatusCode_Running() {
            assertEquals(202, formatter.getHttpStatusCode("RUNNING"));
        }

        @Test
        @DisplayName("Should return 200 for COMPLETED status")
        void testGetHttpStatusCode_Completed() {
            assertEquals(200, formatter.getHttpStatusCode("COMPLETED"));
        }

        @Test
        @DisplayName("Should return 500 for FAILED status")
        void testGetHttpStatusCode_Failed() {
            assertEquals(500, formatter.getHttpStatusCode("FAILED"));
        }

        @Test
        @DisplayName("Should return 500 for unknown status")
        void testGetHttpStatusCode_Unknown() {
            assertEquals(500, formatter.getHttpStatusCode("UNKNOWN_STATUS"));
        }

        @Test
        @DisplayName("Should return 500 for null status")
        void testGetHttpStatusCode_Null() {
            assertEquals(500, formatter.getHttpStatusCode(null));
        }

        @Test
        @DisplayName("Should return 500 for blank status")
        void testGetHttpStatusCode_Blank() {
            assertEquals(500, formatter.getHttpStatusCode("   "));
        }

        @Test
        @DisplayName("Should return 500 for empty status")
        void testGetHttpStatusCode_Empty() {
            assertEquals(500, formatter.getHttpStatusCode(""));
        }
    }

    @Nested
    @DisplayName("isTerminal() - Terminal Status Check")
    class IsTerminalTests {

        @Test
        @DisplayName("Should return true for COMPLETED status")
        void testIsTerminal_Completed() {
            assertTrue(formatter.isTerminal("COMPLETED"));
        }

        @Test
        @DisplayName("Should return true for FAILED status")
        void testIsTerminal_Failed() {
            assertTrue(formatter.isTerminal("FAILED"));
        }

        @Test
        @DisplayName("Should return false for PENDING status")
        void testIsTerminal_Pending() {
            assertFalse(formatter.isTerminal("PENDING"));
        }

        @Test
        @DisplayName("Should return false for RUNNING status")
        void testIsTerminal_Running() {
            assertFalse(formatter.isTerminal("RUNNING"));
        }

        @Test
        @DisplayName("Should return false for unknown status")
        void testIsTerminal_Unknown() {
            assertFalse(formatter.isTerminal("UNKNOWN_STATUS"));
        }

        @Test
        @DisplayName("Should return false for null status")
        void testIsTerminal_Null() {
            assertFalse(formatter.isTerminal(null));
        }

        @Test
        @DisplayName("Should return false for blank status")
        void testIsTerminal_Blank() {
            assertFalse(formatter.isTerminal("   "));
        }

        @Test
        @DisplayName("Should return false for empty status")
        void testIsTerminal_Empty() {
            assertFalse(formatter.isTerminal(""));
        }
    }

    @Nested
    @DisplayName("getNextStatus() - Status Transition")
    class GetNextStatusTests {

        @Test
        @DisplayName("Should return RUNNING for PENDING status")
        void testGetNextStatus_Pending() {
            assertEquals("RUNNING", formatter.getNextStatus("PENDING"));
        }

        @Test
        @DisplayName("Should return COMPLETED for RUNNING status")
        void testGetNextStatus_Running() {
            assertEquals("COMPLETED", formatter.getNextStatus("RUNNING"));
        }

        @Test
        @DisplayName("Should return null for COMPLETED status (terminal)")
        void testGetNextStatus_Completed() {
            assertNull(formatter.getNextStatus("COMPLETED"));
        }

        @Test
        @DisplayName("Should return null for FAILED status (terminal)")
        void testGetNextStatus_Failed() {
            assertNull(formatter.getNextStatus("FAILED"));
        }

        @Test
        @DisplayName("Should return null for unknown status")
        void testGetNextStatus_Unknown() {
            assertNull(formatter.getNextStatus("UNKNOWN_STATUS"));
        }

        @Test
        @DisplayName("Should return PENDING for null status")
        void testGetNextStatus_Null() {
            assertEquals("PENDING", formatter.getNextStatus(null));
        }

        @Test
        @DisplayName("Should return PENDING for blank status")
        void testGetNextStatus_Blank() {
            assertEquals("PENDING", formatter.getNextStatus("   "));
        }

        @Test
        @DisplayName("Should return PENDING for empty status")
        void testGetNextStatus_Empty() {
            assertEquals("PENDING", formatter.getNextStatus(""));
        }

        @Test
        @DisplayName("Should handle lowercase PENDING")
        void testGetNextStatus_PendingLowercase() {
            assertEquals("RUNNING", formatter.getNextStatus("pending"));
        }

        @Test
        @DisplayName("Should handle lowercase RUNNING")
        void testGetNextStatus_RunningLowercase() {
            assertEquals("COMPLETED", formatter.getNextStatus("running"));
        }
    }
}
