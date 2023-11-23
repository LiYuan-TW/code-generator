package com.tw.codegenerator.metadata;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Data
@Slf4j
public class Field {

    private String name;

    private FieldType type;

    private String referencedType;

    private List<String> enumValues;

    public Field(String name, FieldType type, String referencedType, List<String> enumValues) {
        log.info("{} {} {} {}", name, type, referencedType, enumValues);
        if ((FieldType.OBJECT.equals(type) && StringUtils.isEmpty(referencedType)) || (FieldType.ARRAY.equals(type) && StringUtils.isEmpty(referencedType))) {
            throw new IllegalArgumentException("referencedType can not be null when field type is OBJECT or ARRAY");
        }
        if (FieldType.ENUM.equals(type) && CollectionUtils.isEmpty(enumValues)) {
            throw new IllegalArgumentException("enumValues can not be null when field type is OBJECT or ENUM");
        }
        this.name = name;
        this.type = type;
        this.referencedType = referencedType;
        this.enumValues = enumValues;
    }
}
