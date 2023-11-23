package com.tw.common.seedwork;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public abstract class DomainEvent extends ApplicationEvent {

    protected DomainEvent(Object source) {
        super(source);
    }
}
