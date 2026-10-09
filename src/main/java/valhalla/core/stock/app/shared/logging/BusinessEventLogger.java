package valhalla.core.stock.app.shared.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Emits semantic, identifier-only business events. Events inside a transaction are
 * emitted only after its commit, preventing logs for rolled-back changes.
 */
@Component
public class BusinessEventLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessEventLogger.class);

    public void success(String event, String resourceType, Object resourceId) {
        EventContext context = currentContext();
        emitAfterCommit(event, resourceType, resourceId, context);
    }

    public void authenticationSuccess(String event, UUID userId, UUID establishmentId) {
        emitAfterCommit(event, "user", userId, new EventContext(
                userId, establishmentId
        ));
    }

    private void emitAfterCommit(String event, String resourceType, Object resourceId, EventContext context) {
        Runnable logEvent = () -> LOGGER.atInfo()
                .addKeyValue("event", event)
                .addKeyValue("actorId", context.actorId())
                .addKeyValue("establishmentId", context.establishmentId())
                .addKeyValue("resourceType", resourceType)
                .addKeyValue("resourceId", resourceId)
                .log("Business event completed");
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    logEvent.run();
                }
            });
            return;
        }
        logEvent.run();
    }

    private EventContext currentContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return new EventContext(
                    jwt.getToken().getClaimAsString("userId"),
                    jwt.getToken().getClaimAsString("establishmentId")
            );
        }
        return new EventContext(null, null);
    }

    private record EventContext(Object actorId, Object establishmentId) {
    }
}
