package com.tw.common.seedwork;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

@Getter
public abstract class AbstractAggregateRoot extends BaseDomainEntity {

    @JsonIgnore
    protected final List<Object> domainEvents = new ArrayList<>();

    public <T> void registerEvent(T event) {

        Assert.notNull(event, "Domain event must not be null!");

        this.domainEvents.add(event);
    }

    public void clearDomainEvents() {
        this.domainEvents.clear();
    }
}
