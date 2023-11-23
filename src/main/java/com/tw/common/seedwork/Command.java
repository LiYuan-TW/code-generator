package com.tw.common.seedwork;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public abstract class Command {

    private boolean sendEvent;
}
