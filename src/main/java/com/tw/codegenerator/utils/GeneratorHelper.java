package com.tw.codegenerator.utils;

import com.tw.codegenerator.metadata.FieldType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.javapoet.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class GeneratorHelper {

    public static String convertToSnakeCase(String input) {
        // 替换非英文字母和数字字符为下划线
        String cleaned = input.replaceAll("[^A-Za-z0-9]", "_");

        // 将字符串转换为大写，并用下划线拼接
        String[] words = cleaned.split("_");
        StringBuilder snakeCase = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (!snakeCase.isEmpty()) {
                    snakeCase.append("_");
                }
                snakeCase.append(word.toUpperCase());
            }
        }

        return snakeCase.toString();
    }

//    public static String removePluralSuffix(String word) {
//        if (StringUtils.isEmpty(word)) {
//            return word;
//        }
//
//        if (word.endsWith("es")) {
//            return word.substring(0, word.length() - 2);
//        } else if (word.endsWith("s")) {
//            return word.substring(0, word.length() - 1);
//        } else {
//            return word;
//        }
//    }

    public static String removePluralSuffix(String pluralWord) {
        if (pluralWord.endsWith("ies")) {
            // Rule: Replace "ies" with "y" for words ending in "ies"
            return pluralWord.substring(0, pluralWord.length() - 3) + "y";
        } else if (pluralWord.endsWith("s") && !pluralWord.endsWith("ss")) {
            // Rule: Remove "s" for words ending in "s" (except for words ending in "ss")
            return pluralWord.substring(0, pluralWord.length() - 1);
        } else if (pluralWord.endsWith("oes")) {
            // Rule: Replace "oes" with "o" for words ending in "oes"
            return pluralWord.substring(0, pluralWord.length() - 2);
        } else if (pluralWord.endsWith("ses")) {
            // Rule: Replace "ses" with "s" for words ending in "ses"
            return pluralWord.substring(0, pluralWord.length() - 2);
        } else if (pluralWord.endsWith("xes")) {
            // Rule: Replace "xes" with "x" for words ending in "xes"
            return pluralWord.substring(0, pluralWord.length() - 2);
        } else {
            // Default rule: Remove "s" for other cases
            return pluralWord;
        }
    }

    public static String extractLastPathComponent(String path) {
        if (StringUtils.isEmpty(path)) {
            return null;
        }

        var segments = path.split("/");
        if (segments.length > 0) {
            return StringUtils.capitalize(segments[segments.length - 1]);
        } else {
            return null;
        }
    }

    public static void generateFile(String packageName, TypeSpec clazz) {
        var adaptorFile = JavaFile.builder(packageName, clazz).indent("    ").build();
        try {
            adaptorFile.writeTo(new File("src/main/java"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static TypeName getJavaType(FieldType type) {
        switch (type) {
            case INTEGER -> {
                return TypeName.get(Integer.class);
            }
            case NUMBER -> {
                return TypeName.get(Double.class);
            }
            case STRING -> {
                return TypeName.get(String.class);
            }
            case BOOLEAN -> {
                return TypeName.get(Boolean.class);
            }
            default -> throw new IllegalStateException("Unexpected value: " + type);
        }
    }

    public static TypeName getTypeName(String typeName) {
        switch (typeName) {
            // Add more cases for other types as needed
            case "string" -> {
                return ClassName.get(String.class);
            }
            case "number" -> {
                return ClassName.get(Double.class);
            }
            case "integer" -> {
                return TypeName.get(Integer.class);
            }
            case "boolean" -> {
                return TypeName.get(Boolean.class);
            }
            case "array" -> {
                return ParameterizedTypeName.get(ClassName.get(List.class), TypeName.OBJECT);
            }
            default -> throw new IllegalStateException("Unexpected value: " + typeName);
        }
    }
}
