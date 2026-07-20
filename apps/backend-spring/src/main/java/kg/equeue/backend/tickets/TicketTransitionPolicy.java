package kg.equeue.backend.tickets;

import java.util.Map;
import java.util.Set;

public final class TicketTransitionPolicy {

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            TicketStatus.WAITING, Set.of(TicketStatus.CALLED, TicketStatus.CANCELLED, TicketStatus.TRANSFERRED),
            TicketStatus.CALLED, Set.of(TicketStatus.CALLED, TicketStatus.IN_SERVICE, TicketStatus.CANCELLED, TicketStatus.NO_SHOW, TicketStatus.TRANSFERRED),
            TicketStatus.IN_SERVICE, Set.of(TicketStatus.PAUSED, TicketStatus.COMPLETED, TicketStatus.CANCELLED),
            TicketStatus.PAUSED, Set.of(TicketStatus.IN_SERVICE, TicketStatus.CANCELLED)
    );

    private TicketTransitionPolicy() {
    }

    public static boolean canTransition(TicketStatus from, TicketStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
