package com.tw.common.criteria;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.Collection;

@UtilityClass
public class SpecificationBuilder {

    public static <T> Specification<T> build(Condition condition) {
        return (root, query, criteriaBuilder) -> {
            query.distinct(true);
            return getPredicate(condition, criteriaBuilder, getPath(condition.getFieldName(), root));
        };
    }

    public static <T> Specification<T> build(QuerySchema querySchema) {

        return (root, query, criteriaBuilder) -> {
            query.distinct(true);

            var predicates = querySchema.getConditions().stream()
                    .map(condition -> getPredicate(condition, criteriaBuilder, getPath(condition.getFieldName(), root)))
                    .toList();

            return switch (querySchema.getLogicalOperator()) {
                case AND -> criteriaBuilder.and(predicates.toArray(Predicate[]::new));
                case OR -> criteriaBuilder.or(predicates.toArray(Predicate[]::new));
            };
        };
    }

    private <E> Path<?> getPath(String fieldName, Root<E> root) {
        fieldName = getFieldName(fieldName, root);

        if (fieldName.contains(".")) {
            var tableName = fieldName.split("\\.")[0];
            var subFieldName = fieldName.split("\\.")[1];

            var fieldAsPath = root.get(tableName);
            if (!Collection.class.isAssignableFrom(fieldAsPath.getJavaType())) {
                throw new IllegalArgumentException("Field %s is not type of Collection.".formatted(fieldName));
            }

            return root.getJoins().stream()
                    .filter(join -> StringUtils.equals(join.getAttribute().getName(), tableName))
                    .findFirst()
                    .orElseGet(() -> root.join(tableName))
                    .get(subFieldName);
        } else {
            return root.get(fieldName);
        }
    }

    private static <E> String getFieldName(String fieldName, Root<E> root) {
        Class<? extends E> clazz = root.getJavaType();

        if (notMainTableField(fieldName, clazz)) {
            var mainTableFieldName = getMainTableFieldName(fieldName, clazz);
            return mainTableFieldName + "." + fieldName;
        }
        return fieldName;
    }

    private static <E> boolean notMainTableField(String fieldName, Class<? extends E> clazz) {
        return Arrays.stream(clazz.getDeclaredFields()).noneMatch(field -> {
            var isCollection = Collection.class.isAssignableFrom(field.getDeclaringClass());
            return !isCollection && field.getName().equals(fieldName);
        });
    }

    private static <E> String getMainTableFieldName(String fieldName, Class<? extends E> clazz) {
        String mainTableFieldName = null;

        for (Field field : clazz.getDeclaredFields()) {
            if (Collection.class.isAssignableFrom(field.getType())) {
                var childFields = ((Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0])
                        .getDeclaredFields();
                for (Field childField : childFields) {
                    if (childField.getName().equals(fieldName)) {
                        mainTableFieldName = field.getName();
                        break;
                    }
                }
            }
        }

        if (StringUtils.isEmpty(mainTableFieldName)) {
            throw new IllegalArgumentException("Can not found field fieldName");
        }

        return mainTableFieldName;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Predicate getPredicate(Condition condition, CriteriaBuilder criteriaBuilder, Path path) {

        var comparatorOperator = condition.getComparatorOperator();
        var value = condition.getValue();
        var valueEnd = condition.getValueEnd();

        return switch (comparatorOperator) {
            case EQUAL -> value == null ? criteriaBuilder.isNull(path) : criteriaBuilder.equal(path, value);

            case NOT_EQUAL -> value == null ? criteriaBuilder.isNotNull(path) : criteriaBuilder.notEqual(path, value);

            case LIKE -> criteriaBuilder.like(path, "%" + value.toString() + "%");

            case EQUAL_IGNORE_CASE -> criteriaBuilder.equal(
                    criteriaBuilder.upper(path), value.toString().toUpperCase());

            case BETWEEN -> criteriaBuilder.between(path, (Comparable) value, (Comparable) valueEnd);

            case GREATER_THAN -> criteriaBuilder.greaterThan(path, (Comparable) value);

            case LESS_THAN -> criteriaBuilder.lessThan(path, (Comparable) value);

            case IN -> path.in((Collection) value);

            case LIKE_IGNORE_CASE -> criteriaBuilder.like(
                    criteriaBuilder.upper(path), "%" + value.toString().toUpperCase() + "%");
        };
    }
}
