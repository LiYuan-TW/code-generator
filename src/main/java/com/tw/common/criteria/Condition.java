package com.tw.common.criteria;

import lombok.Getter;

import java.util.List;

import static com.tw.common.criteria.ComparatorOperator.*;


@Getter
public class Condition {

    private final String fieldName;

    private final ComparatorOperator comparatorOperator;

    private final Object value;

    private Object valueEnd;

    public Condition(String fieldName, Object value) {
        this.fieldName = fieldName;
        this.comparatorOperator = EQUAL;
        this.value = value;
    }

    public Condition(String fieldName, Object value, ComparatorOperator comparatorOperator) {
        var supportOperator = List.of(LIKE, EQUAL_IGNORE_CASE, GREATER_THAN, LESS_THAN, IN, LIKE_IGNORE_CASE);
        if (supportOperator.contains(comparatorOperator) && value == null) {
            throw new IllegalArgumentException("Value must not be null.");
        }
        this.fieldName = fieldName;
        this.value = value;
        this.comparatorOperator = comparatorOperator;
    }

    public Condition(String fieldName, Object valueBegin, Object valueEnd) {
        if (valueBegin == null) {
            throw new IllegalArgumentException("Begin value must not be null.");
        }
        if (valueEnd == null) {
            throw new IllegalArgumentException("End value must not be null.");
        }
        this.fieldName = fieldName;
        this.comparatorOperator = BETWEEN;
        this.value = valueBegin;
        this.valueEnd = valueEnd;
    }
}
