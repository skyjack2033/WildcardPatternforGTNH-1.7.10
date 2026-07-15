package com.myname.wildcardpattern.compat.gtnl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public final class GTNLPatternSlotState<T> {

    private final T representative;
    private final Function<T, String> idExtractor;
    private List<T> expandedDetails = Collections.emptyList();
    private T active;
    private String activeId = "";
    private boolean unresolved;

    public GTNLPatternSlotState(T representative, Function<T, String> idExtractor) {
        this.representative = representative;
        this.idExtractor = idExtractor;
    }

    public void replaceExpandedDetails(Collection<T> details) {
        Map<String, T> unique = new LinkedHashMap<>();
        if (details != null) {
            for (T detail : details) {
                String id = idOf(detail);
                if (!id.isEmpty()) {
                    unique.putIfAbsent(id, detail);
                }
            }
        }
        this.expandedDetails = Collections.unmodifiableList(new ArrayList<>(unique.values()));
        if (!this.activeId.isEmpty()) {
            T replacement = findById(this.activeId);
            if (replacement == null) {
                this.active = null;
                this.activeId = "";
                this.unresolved = true;
            } else {
                this.active = replacement;
            }
        }
    }

    public List<T> getExpandedDetails() {
        return this.expandedDetails;
    }

    public boolean activate(T requested, boolean occupied) {
        T canonical = findById(idOf(requested));
        if (canonical == null) {
            return false;
        }
        String requestedId = idOf(canonical);
        if (occupied && (this.unresolved || this.activeId.isEmpty() || !this.activeId.equals(requestedId))) {
            return false;
        }
        this.active = canonical;
        this.activeId = requestedId;
        this.unresolved = false;
        return true;
    }

    public T recover(String savedId, boolean occupied, Predicate<T> inputsMatch) {
        if (!occupied) {
            clear();
            return this.representative;
        }

        T byId = findById(normalize(savedId));
        if (byId != null) {
            setActive(byId);
            return byId;
        }

        T unique = null;
        for (T candidate : this.expandedDetails) {
            if (!inputsMatch.test(candidate)) {
                continue;
            }
            if (unique != null && !idOf(unique).equals(idOf(candidate))) {
                this.active = null;
                this.activeId = "";
                this.unresolved = true;
                return null;
            }
            unique = candidate;
        }

        if (unique != null) {
            setActive(unique);
            return unique;
        }
        this.active = null;
        this.activeId = "";
        this.unresolved = true;
        return null;
    }

    public T current(boolean occupied) {
        if (!occupied) {
            clear();
            return this.representative;
        }
        return this.unresolved ? null : this.active;
    }

    public String getPersistentId(boolean occupied) {
        return occupied && !this.unresolved ? this.activeId : "";
    }

    public String getActiveId() {
        return this.activeId;
    }

    public boolean isUnresolved() {
        return this.unresolved;
    }

    public Snapshot<T> snapshot() {
        return new Snapshot<>(this.active, this.activeId, this.unresolved);
    }

    public void restore(Snapshot<T> snapshot) {
        this.active = snapshot.active;
        this.activeId = snapshot.activeId;
        this.unresolved = snapshot.unresolved;
    }

    private T findById(String id) {
        if (id.isEmpty()) {
            return null;
        }
        for (T detail : this.expandedDetails) {
            if (id.equals(idOf(detail))) {
                return detail;
            }
        }
        return null;
    }

    private void setActive(T detail) {
        this.active = detail;
        this.activeId = idOf(detail);
        this.unresolved = false;
    }

    private void clear() {
        this.active = null;
        this.activeId = "";
        this.unresolved = false;
    }

    private String idOf(T value) {
        return value == null ? "" : normalize(this.idExtractor.apply(value));
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }

    public static final class Snapshot<T> {

        private final T active;
        private final String activeId;
        private final boolean unresolved;

        private Snapshot(T active, String activeId, boolean unresolved) {
            this.active = active;
            this.activeId = activeId;
            this.unresolved = unresolved;
        }
    }
}
