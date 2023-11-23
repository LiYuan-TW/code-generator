package com.tw.codegenerator.utils;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.CaseUtils;
import org.springframework.javapoet.ClassName;
import org.springframework.javapoet.ParameterizedTypeName;
import org.springframework.javapoet.TypeName;
import org.springframework.javapoet.TypeSpec;
import org.springframework.util.CollectionUtils;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Map;

import static com.tw.codegenerator.utils.GeneratorHelper.*;
import static com.tw.codegenerator.utils.GeneratorHelper.removePluralSuffix;

@Slf4j
public class OpenApiHelper {

    private static final ClassName getterAnnotationClass = ClassName.get("lombok", "Getter");

    private static final ClassName builderAnnotationClass = ClassName.get("lombok", "Builder");

    private static final ClassName superBuilderAnnotationClass = ClassName.get("lombok.experimental", "SuperBuilder");

    private final String basePackage;

    private final String dtoPackage;

    private final String requestPackage;

    private final String responsePackage;

    private final String suffix;

    public OpenApiHelper(String basePackage, String suffix) {
        this.basePackage = basePackage;
        this.dtoPackage = basePackage + ".app.dto";
        this.requestPackage = basePackage + ".resource.controller.request";
        this.responsePackage = basePackage + ".resource.controller.response";
        this.suffix = suffix;
    }

    public void generateBySchemas(Map<String, Schema> schemas) {
        for (String schemaName : schemas.keySet()) {
            var classSchema = schemas.get(schemaName);
            var className = StringUtils.capitalize(schemaName.replace(" ", ""));

            generateBySchema(className, classSchema, dtoPackage);
        }
    }

    public void generateByRequestBodies(Map<String, RequestBody> requestBodies) {
        for (String bodyName : requestBodies.keySet()) {
            var requestBody = requestBodies.get(bodyName);

            var classSchema = requestBody.getContent().get("application/json").getSchema();
            var className = StringUtils.capitalize(bodyName.replace(" ", ""));

            generateBySchema(className, classSchema, requestPackage);
        }
    }

    public void generateByResponse(Map<String, ApiResponse> responses) {
        for (String responseName : responses.keySet()) {
            var response = responses.get(responseName);

            var classSchema = response.getContent().get("application/json").getSchema();
            var className = StringUtils.capitalize(responseName.replace(" ", ""));

            generateBySchema(className, classSchema, responsePackage);
        }
    }

    public void generateByResponses(Map<String, ApiResponses> responses) {
        for (String controllerName : responses.keySet()) {
            var apiResponses = responses.get(controllerName);

            var classSchema = apiResponses.get("200").getContent().get("application/json").getSchema();
            var className = StringUtils.capitalize(controllerName) + "Response";

            generateBySchema(className, classSchema, responsePackage);
        }
    }

    private void generateBySchema(String className, Schema classSchema, String packageName) {
        var classBuilder = TypeSpec.classBuilder(className + suffix)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(getterAnnotationClass)
                .addAnnotation(superBuilderAnnotationClass);

        if (!CollectionUtils.isEmpty(classSchema.getProperties())) {
            generateFieldsAndInnerClasses(classBuilder, packageName, className, classSchema.getProperties());
        }

        var ref = classSchema.get$ref();
        if (ref != null) {
            classBuilder.superclass(getRefTypeName(ref, basePackage));
        }

        generateFile(packageName, classBuilder.build());
    }

    private void generateFieldsAndInnerClasses(TypeSpec.Builder classBuilder, String packageName, String className, Map<String, Schema> properties) {
        if (properties == null) {
            return;
        }
        for (String fieldName : properties.keySet()) {
            var fieldSchema = properties.get(fieldName);
            fieldName = CaseUtils.toCamelCase(fieldName, false, '_');
            var fieldType = fieldSchema.getType();
            var ref = fieldSchema.get$ref();

            if (ref != null) {
                var refClassName = getRefTypeName(ref, basePackage);
                classBuilder.addField(refClassName, fieldName, Modifier.PRIVATE, Modifier.FINAL);
                continue;
            }

            var innerClassName = StringUtils.capitalize(fieldName);
            var innerFieldType = ClassName.get(packageName, className + suffix + "." + innerClassName);

            switch (fieldType) {
                case "object" ->
                        handleObjectType(classBuilder, packageName, className, fieldName, innerClassName, fieldSchema, innerFieldType);
                case "array" ->
                        handleArrayType(classBuilder, packageName, className, fieldName, fieldSchema, innerClassName);
                case "integer" -> classBuilder.addField(Integer.class, fieldName, Modifier.PRIVATE, Modifier.FINAL);
                case "number" -> classBuilder.addField(Double.class, fieldName, Modifier.PRIVATE, Modifier.FINAL);
                case "string" -> handleStringType(classBuilder, fieldName, fieldSchema, innerClassName, innerFieldType);
                case "boolean" -> classBuilder.addField(Boolean.class, fieldName, Modifier.PRIVATE, Modifier.FINAL);
                default -> throw new IllegalStateException("Unexpected value: " + fieldType);
            }
        }
    }

    private void handleObjectType(TypeSpec.Builder classBuilder, String packageName, String className, String fieldName, String innerClassName, Schema fieldSchema, ClassName innerFieldType) {
        var innerClassBuilder = TypeSpec.classBuilder(innerClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addAnnotation(getterAnnotationClass)
                .addAnnotation(builderAnnotationClass);
        generateFieldsAndInnerClasses(innerClassBuilder, packageName + "." + className, innerClassName, fieldSchema.getProperties());
        classBuilder.addType(innerClassBuilder.build());
        classBuilder.addField(innerFieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
    }

    private void handleStringType(TypeSpec.Builder classBuilder, String fieldName, Schema fieldSchema, String innerClassName, ClassName innerFieldType) {
        if (fieldSchema.getEnum() != null) {
            var enumBuilder = TypeSpec.enumBuilder(innerClassName).addModifiers(Modifier.PUBLIC);
            for (Object enumValue : fieldSchema.getEnum()) {
                enumBuilder.addEnumConstant(convertToSnakeCase((String) enumValue));
            }
            classBuilder.addType(enumBuilder.build());
            classBuilder.addField(innerFieldType, fieldName, Modifier.PRIVATE, Modifier.FINAL);
        } else {
            classBuilder.addField(String.class, fieldName, Modifier.PRIVATE, Modifier.FINAL);
        }
    }

    private void handleArrayType(TypeSpec.Builder classBuilder, String packageName, String className, String fieldName, Schema fieldSchema, String innerClassName) {
        var items = fieldSchema.getItems();
        var itemRef = items.get$ref();
        if (itemRef != null) {
            var refClassName = getRefTypeName(itemRef, basePackage);
            log.info(refClassName.toString());
            classBuilder.addField(ParameterizedTypeName.get(ClassName.get(List.class), refClassName), fieldName, Modifier.PRIVATE, Modifier.FINAL);
        } else {
            if (CollectionUtils.isEmpty(items.getProperties())) {
                classBuilder.addField(ParameterizedTypeName.get(ClassName.get(List.class), TypeName.get(Object.class)), fieldName, Modifier.PRIVATE, Modifier.FINAL);
            } else {
                innerClassName = removePluralSuffix(innerClassName);
                var innerClassBuilder = TypeSpec.classBuilder(innerClassName)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .addAnnotation(getterAnnotationClass)
                        .addAnnotation(builderAnnotationClass);
                generateFieldsAndInnerClasses(innerClassBuilder, packageName + "." + className, innerClassName, items.getProperties());
                classBuilder.addType(innerClassBuilder.build());
                var refClassName = ClassName.get(packageName + "." + className + suffix, innerClassName);
                classBuilder.addField(ParameterizedTypeName.get(ClassName.get(List.class), refClassName), fieldName, Modifier.PRIVATE, Modifier.FINAL);
            }
        }
    }

    public static String getRefClassNamePackageName(String ref, String basePackage) {
       var dtoPackage = basePackage + ".app.dto";
       var requestPackage = basePackage + ".resource.controller.request";
       var responsePackage = basePackage + ".resource.controller.response";

        String refClassNamePackageName = null;
        if (ref.contains("/responses/")) {
            refClassNamePackageName = responsePackage;
        } else if (ref.contains("/requestBodies/")) {
            refClassNamePackageName = requestPackage;
        } else if (ref.contains("/schemas/")) {
            refClassNamePackageName = dtoPackage;
        }
        return refClassNamePackageName;
    }

    public static String getRefClassNameSuffix(String ref) {
        String suffix = null;
        if (ref.contains("/responses/")) {
            suffix = "";
        } else if (ref.contains("/requestBodies/")) {
            suffix = "";
        } else if (ref.contains("/schemas/")) {
            suffix = "DTO";
        }
        return suffix;
    }

    public static String getRefClassName(String ref) {
        String suffix = null;
        if (ref.contains("/responses/")) {
            suffix = "";
        } else if (ref.contains("/requestBodies/")) {
            suffix = "";
        } else if (ref.contains("/schemas/")) {
            suffix = "DTO";
        }
        return extractLastPathComponent(ref) + suffix;
    }

    public static ClassName getRefTypeName(String ref, String basePackage) {
        return ClassName.get(getRefClassNamePackageName(ref, basePackage), getRefClassName(ref));
    }
}
