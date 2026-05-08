package com.epam.execution_engine_service.util;

import com.epam.execution_engine_service.gateway.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Extracts and validates client IP address from HTTP request
 * Handles proxied requests (X-Forwarded-For, X-Real-IP headers)
 * Validates IP format to prevent malformed addresses (SRS Section 2.2)
 */
@Component
public class IpAddressExtractor {
    
    // IPv4 pattern: xxx.xxx.xxx.xxx where each part is 1-3 digits
    private static final String IPV4_PATTERN = "^(\\d{1,3}\\.){3}\\d{1,3}$";
    // IPv6 pattern: contains colons and hexadecimal characters
    private static final String IPV6_PATTERN = "^[\\da-fA-F:]+$";
    
    /**
     * Extracts the client IP address from the request
     * Considers proxy headers: X-Forwarded-For, X-Real-IP
     * Validates IP format to ensure proper rate limiting (SRS Section 3.3)
     * @param request The HTTP request
     * @return The validated client IP address
     * @throws ValidationException if IP cannot be extracted or is invalid
     */
    public String extractClientIp(HttpServletRequest request) {
        // Check X-Forwarded-For header first (for load balancer/proxy)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            String ip = xForwardedFor.split(",")[0].trim();
            validateIpFormat(ip);
            return ip;
        }
        
        // Check X-Real-IP header
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            validateIpFormat(xRealIp);
            return xRealIp;
        }
        
        // Fall back to direct remote address
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr != null && !remoteAddr.isEmpty()) {
            validateIpFormat(remoteAddr);
            return remoteAddr;
        }
        
        throw new ValidationException("Unable to extract client IP address");
    }
    
    /**
     * Validates the IP address format (IPv4 or IPv6)
     * @param ip The IP address to validate
     * @throws ValidationException if IP format is invalid
     */
    private void validateIpFormat(String ip) {
        if (!isValidIpAddress(ip)) {
            throw new ValidationException(
                    "Invalid IP address format: " + ip + 
                    ". Expected IPv4 (xxx.xxx.xxx.xxx) or IPv6 format");
        }
    }
    
    /**
     * Checks if the given string is a valid IPv4 or IPv6 address
     * @param ip The IP address to check
     * @return true if valid, false otherwise
     */
    private boolean isValidIpAddress(String ip) {
        // Check IPv4 format
        if (ip.matches(IPV4_PATTERN)) {
            return isValidIpv4(ip);
        }
        // Check IPv6 format (simplified check)
        if (ip.matches(IPV6_PATTERN)) {
            return true;
        }
        return false;
    }
    
    /**
     * Validates IPv4 address octets are in valid range (0-255)
     * @param ipv4 The IPv4 address to validate
     * @return true if all octets are valid, false otherwise
     */
    private boolean isValidIpv4(String ipv4) {
        String[] parts = ipv4.split("\\.");
        for (String part : parts) {
            try {
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}
