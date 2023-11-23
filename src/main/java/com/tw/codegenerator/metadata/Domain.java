package com.tw.codegenerator.metadata;

import lombok.Data;

import java.util.List;

@Data
public class Domain {

    private String typeName;

    private DomainType domainType;

    private boolean isAggregateRoot;

    private List<Field> fields;

    private List<MethodType> methods;
}