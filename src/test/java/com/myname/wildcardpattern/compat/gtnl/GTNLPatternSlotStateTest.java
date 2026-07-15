package com.myname.wildcardpattern.compat.gtnl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class GTNLPatternSlotStateTest {

    private static final String REPRESENTATIVE = "display";

    @Test
    void emptySlotCanSelectEitherGeneratedDetail() {
        GTNLPatternSlotState<String> state = state("bronze", "aluminium");

        assertTrue(state.activate("bronze", false));
        assertEquals("bronze", state.current(true));
        assertTrue(state.activate("aluminium", false));
        assertEquals("aluminium", state.current(true));
    }

    @Test
    void occupiedSlotAcceptsOnlyItsActiveGeneratedId() {
        GTNLPatternSlotState<String> state = state("bronze", "aluminium");
        assertTrue(state.activate("bronze", false));

        assertTrue(state.activate("bronze", true));
        assertFalse(state.activate("aluminium", true));
        assertEquals("bronze", state.current(true));
    }

    @Test
    void emptyingSlotRestoresRepresentativeDetail() {
        GTNLPatternSlotState<String> state = state("bronze");
        assertTrue(state.activate("bronze", false));

        assertEquals(REPRESENTATIVE, state.current(false));
        assertEquals("", state.getPersistentId(false));
    }

    @Test
    void savedGeneratedIdWinsDuringReload() {
        GTNLPatternSlotState<String> state = state("bronze", "aluminium");

        assertEquals("aluminium", state.recover("aluminium", true, value -> false));
        assertEquals("aluminium", state.getPersistentId(true));
        assertFalse(state.isUnresolved());
    }

    @Test
    void missingSavedIdUsesOneUniqueInputMatch() {
        GTNLPatternSlotState<String> state = state("bronze", "aluminium");

        assertEquals("bronze", state.recover("removed-id", true, "bronze"::equals));
        assertFalse(state.isUnresolved());
    }

    @Test
    void ambiguousInputMatchNeverChoosesAnArbitraryDetail() {
        GTNLPatternSlotState<String> state = state("bronze", "aluminium");

        assertNull(state.recover("", true, value -> true));
        assertNull(state.current(true));
        assertTrue(state.isUnresolved());
        assertFalse(state.activate("bronze", true));
    }

    @Test
    void failedInsertionCanRestorePreviousSelection() {
        GTNLPatternSlotState<String> state = state("bronze", "aluminium");
        assertTrue(state.activate("bronze", false));
        GTNLPatternSlotState.Snapshot<String> before = state.snapshot();
        assertTrue(state.activate("aluminium", false));

        state.restore(before);

        assertEquals("bronze", state.current(true));
        assertEquals("bronze", state.getActiveId());
    }

    @Test
    void duplicateGeneratedIdsAreRegisteredOnce() {
        GTNLPatternSlotState<String> state = new GTNLPatternSlotState<>(REPRESENTATIVE, value -> value);

        state.replaceExpandedDetails(Arrays.asList("bronze", "bronze", "aluminium", null, ""));

        assertEquals(Arrays.asList("bronze", "aluminium"), state.getExpandedDetails());
    }

    private static GTNLPatternSlotState<String> state(String... details) {
        GTNLPatternSlotState<String> state = new GTNLPatternSlotState<>(REPRESENTATIVE, value -> value);
        state.replaceExpandedDetails(Arrays.asList(details));
        return state;
    }
}
