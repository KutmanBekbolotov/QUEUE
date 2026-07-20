package kg.equeue.backend.tickets;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicketTransitionPolicyTest {

    @Test
    void allowsRequiredLifecycleTransitions() {
        assertThat(TicketTransitionPolicy.canTransition(TicketStatus.WAITING, TicketStatus.CALLED)).isTrue();
        assertThat(TicketTransitionPolicy.canTransition(TicketStatus.CALLED, TicketStatus.CALLED)).isTrue();
        assertThat(TicketTransitionPolicy.canTransition(TicketStatus.CALLED, TicketStatus.IN_SERVICE)).isTrue();
        assertThat(TicketTransitionPolicy.canTransition(TicketStatus.IN_SERVICE, TicketStatus.PAUSED)).isTrue();
        assertThat(TicketTransitionPolicy.canTransition(TicketStatus.PAUSED, TicketStatus.IN_SERVICE)).isTrue();
        assertThat(TicketTransitionPolicy.canTransition(TicketStatus.IN_SERVICE, TicketStatus.COMPLETED)).isTrue();
        assertThat(TicketTransitionPolicy.canTransition(TicketStatus.CALLED, TicketStatus.NO_SHOW)).isTrue();
        assertThat(TicketTransitionPolicy.canTransition(TicketStatus.WAITING, TicketStatus.TRANSFERRED)).isTrue();
    }

    @Test
    void rejectsInvalidLifecycleTransitions() {
        assertThat(TicketTransitionPolicy.canTransition(TicketStatus.WAITING, TicketStatus.COMPLETED)).isFalse();
        assertThat(TicketTransitionPolicy.canTransition(TicketStatus.COMPLETED, TicketStatus.CANCELLED)).isFalse();
        assertThat(TicketTransitionPolicy.canTransition(TicketStatus.NO_SHOW, TicketStatus.IN_SERVICE)).isFalse();
    }
}
