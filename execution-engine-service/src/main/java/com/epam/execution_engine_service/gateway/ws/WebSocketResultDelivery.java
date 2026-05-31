package com.epam.execution_engine_service.gateway.ws;

import com.epam.execution_engine_service.domain.ExecutionResultEvent;

/**
 * DIP/ISP: Abstraction for delivering execution results to connected clients.
 * {@link RedisSubscriberService} depends on this interface, not on STOMP/SimpMessaging directly.
 */
public interface WebSocketResultDelivery {

    /**
     * Deliver the given execution result to the owning user's WebSocket session.
     */
    void deliver(ExecutionResultEvent result);
}

