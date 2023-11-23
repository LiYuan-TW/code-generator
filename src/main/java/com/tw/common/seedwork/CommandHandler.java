package com.tw.common.seedwork;

public interface CommandHandler<T, C extends Command> {

    T execute(C command);

}
