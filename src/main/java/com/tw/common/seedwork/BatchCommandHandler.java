package com.tw.common.seedwork;

import java.util.List;

public interface BatchCommandHandler<T extends BaseDomainEntity, C extends Command> {

    List<T> execute(C command);

}
