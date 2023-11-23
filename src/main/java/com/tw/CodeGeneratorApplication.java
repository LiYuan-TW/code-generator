package com.tw;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CodeGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeGeneratorApplication.class, args);
    }
//    public static void main(String[] args) {
//        var order = buildOrder();
//        var orderItem = buildOrderItem();
//        var money = buildMoney();
//
//        generate(List.of(order, orderItem, money));
//    }
}
