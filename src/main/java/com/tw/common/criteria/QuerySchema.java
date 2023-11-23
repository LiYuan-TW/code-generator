package com.tw.common.criteria;

import lombok.Getter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.tw.common.criteria.LogicalOperator.AND;


@Getter
public class QuerySchema {

    private List<Condition> conditions = new ArrayList<>();

    private LogicalOperator logicalOperator = AND;

    private Pageable pageable = PageRequest.of(0, Integer.MAX_VALUE);

    public QuerySchema(Map<String, Object> queryParams) {
        queryParams.forEach((fieldName, value) -> conditions.add(new Condition(fieldName, value)));
    }

    public QuerySchema(List<Condition> conditions) {
        this.conditions = conditions;
    }

    public QuerySchema(Pageable pageable, List<Condition> conditions) {
        this.pageable = pageable;
        this.conditions = conditions;
    }

    public QuerySchema(Condition... conditions) {
        this.conditions = List.of(conditions);
    }

    public QuerySchema(Pageable pageable, Condition... conditions) {
        this.pageable = pageable;
        this.conditions = List.of(conditions);
    }

    public QuerySchema(LogicalOperator logicalOperator, List<Condition> conditions) {
        this.logicalOperator = logicalOperator;
        this.conditions = conditions;
    }

    public QuerySchema(LogicalOperator logicalOperator, Pageable pageable, List<Condition> conditions) {
        this.logicalOperator = logicalOperator;
        this.pageable = pageable;
        this.conditions = conditions;
    }

    public QuerySchema(LogicalOperator logicalOperator, Condition... conditions) {
        this.logicalOperator = logicalOperator;
        this.conditions = List.of(conditions);
    }

    public QuerySchema(LogicalOperator logicalOperator, Pageable pageable, Condition... conditions) {
        this.logicalOperator = logicalOperator;
        this.pageable = pageable;
        this.conditions = List.of(conditions);
    }
}
