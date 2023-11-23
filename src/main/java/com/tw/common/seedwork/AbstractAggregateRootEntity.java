package com.tw.common.seedwork;

import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AbstractAggregateRootEntity extends BaseEntity {

    @Transient
    protected final List<Object> domainEvents = new ArrayList<>();

    public void setDomainEvents(List<Object> domainEvents) {
        this.domainEvents.clear();
        this.domainEvents.addAll(domainEvents);
    }

    /**
     * Clears all domain events currently held. Usually invoked by the infrastructure in place in Spring Data
     * repositories.
     */
    @AfterDomainEventPublication
    public void clearDomainEvents() {
        this.domainEvents.clear();
    }

    /**
     * All domain events currently captured by the aggregate.
     */
    @DomainEvents
    public Collection<Object> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}
