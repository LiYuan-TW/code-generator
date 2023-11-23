package com.tw.codegenerator.builder;

import com.tw.codegenerator.metadata.*;

import java.util.List;

public class DomainMetadataBuilder {

    public static List<Domain> buildAll() {
        var order = buildOrder();
        var item = buildItem();
        var money = buildMoney();
        var address = buildAddress();
        var product = buildProduct();
        var paymentInfo = buildPaymentInfo();
        var buyer = buildBuyer();
        return List.of(order, item, money, address, product, paymentInfo, buyer);
    }

    public static Domain buildOrder() {
        var order = new Domain();
        order.setTypeName("Order");
        order.setDomainType(DomainType.ENTITY);
        order.setAggregateRoot(true);

        var buyer = new Field("buyer", FieldType.OBJECT, "User", null);
        var items = new Field("items", FieldType.ARRAY, "Item", null);
        var totalPrice = new Field("totalPrice", FieldType.OBJECT, "Money", null);
        var paid = new Field("paid", FieldType.BOOLEAN, null, null);
        var paymentInfo = new Field("paymentInfo", FieldType.OBJECT, "PaymentInfo", null);
        var status = new Field("status", FieldType.ENUM, "Order.Status", List.of("PAID", "UNPAID"));

        order.setFields(List.of(buyer, items, totalPrice, paid, paymentInfo, status));

        order.setMethods(List.of(MethodType.CREATE, MethodType.UPDATE, MethodType.DELETE, /*MethodType.BATCH_CREATE,
                MethodType.BATCH_UPDATE, MethodType.BATCH_DELETE,*/ MethodType.QUERY_BY_ID, MethodType.QUERY_BY_CRITERIA));

        return order;
    }

    public static Domain buildItem() {
        var item = new Domain();
        item.setTypeName("Item");
        item.setDomainType(DomainType.ENTITY);
        item.setAggregateRoot(false);

        var product = new Field("product", FieldType.OBJECT, "Product", null);
        var totalPrice = new Field("totalPrice", FieldType.OBJECT, "Money", null);

        item.setFields(List.of(product, totalPrice));

        return item;
    }

    public static Domain buildMoney() {
        var money = new Domain();
        money.setTypeName("Money");
        money.setDomainType(DomainType.VALUE_OBJECT);
        money.setAggregateRoot(false);

        var amount = new Field("amount", FieldType.NUMBER, null, null);
        var currency = new Field("currency", FieldType.STRING, null, null);
        money.setFields(List.of(amount, currency));

        return money;
    }

    public static Domain buildAddress() {
        var address = new Domain();
        address.setTypeName("Address");
        address.setDomainType(DomainType.VALUE_OBJECT);
        address.setAggregateRoot(false);

        var country = new Field("country", FieldType.STRING, null, null);
        var city = new Field("city", FieldType.STRING, null, null);
        var addressLine = new Field("addressLine", FieldType.STRING, null, null);
        address.setFields(List.of(country, city, addressLine));

        return address;
    }

    public static Domain buildProduct() {
        var product = new Domain();
        product.setTypeName("Product");
        product.setDomainType(DomainType.ENTITY);
        product.setAggregateRoot(false);

        var name = new Field("name", FieldType.STRING, null, null);
        var price = new Field("price", FieldType.OBJECT, "Money", null);
        var addresses = new Field("addresses", FieldType.ARRAY, "Address", null);
        product.setFields(List.of(name, price, addresses));

        return product;
    }

    public static Domain buildPaymentInfo() {
        var paymentInfo = new Domain();
        paymentInfo.setTypeName("PaymentInfo");
        paymentInfo.setDomainType(DomainType.VALUE_OBJECT);
        paymentInfo.setAggregateRoot(false);

        var time = new Field("time", FieldType.INTEGER, null, null);
        var amount = new Field("amount", FieldType.OBJECT, "Money", null);
        paymentInfo.setFields(List.of(time, amount));

        return paymentInfo;
    }

    public static Domain buildBuyer() {
        var buyer = new Domain();
        buyer.setTypeName("User");
        buyer.setDomainType(DomainType.ENTITY);
        buyer.setAggregateRoot(false);

        var name = new Field("name", FieldType.STRING, null, null);
        var age = new Field("age", FieldType.INTEGER, "Money", null);
        var sex = new Field("sex", FieldType.ENUM, "User.Sex", List.of("MALE", "FEMALE"));
        var address = new Field("address", FieldType.OBJECT, "Address", null);
        buyer.setFields(List.of(name, age, sex, address));

        return buyer;
    }
}
