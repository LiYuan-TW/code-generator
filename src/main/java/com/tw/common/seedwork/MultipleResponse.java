package com.tw.common.seedwork;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

import java.util.List;
import java.util.Map;

public record MultipleResponse<T>(String totalItems, @JsonAnyGetter Map<String, List<T>> items) {

    public MultipleResponse(String key, List<T> items) {
        this(String.valueOf(items.size()), Map.of(key, items));
    }

    public MultipleResponse(long totalItems, String key, List<T> items) {
        this(String.valueOf(totalItems), Map.of(key, items));
    }
}
