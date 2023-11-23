package com.tw.codegenerator.javapoet;

import com.tw.codegenerator.metadata.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.springframework.data.domain.Page;
import org.springframework.javapoet.*;
import org.springframework.util.CollectionUtils;

import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import static com.tw.codegenerator.utils.GeneratorHelper.convertToSnakeCase;
import static com.tw.codegenerator.utils.GeneratorHelper.generateFile;

@Slf4j
public class DomainLayerGenerator {

    private final String entityPackage;

    private final String valueObjectPackage;

    private final String commandPackage;

    private final String eventPackage;

    private final String adaptorPackage;

    private Domain aggregateRoot;

    private String uncapitalizedRootName;

    private String rootName;

    private final Map<String, String> domainToPackage = new HashMap<>();

    private static final String SEEDWORK_PACKAGE = "com.tw.common.seedwork";

    private static final ClassName getterAnnotationClass = ClassName.get("lombok", "Getter");

    private static final ClassName superBuilderAnnotationClass = ClassName.get("lombok.experimental", "SuperBuilder");

    private static final ClassName builderDefaultAnnotationClass = ClassName.get("lombok.Builder", "Default");

    private static final ClassName allArgsConstructorAnnotationClass = ClassName.get("lombok", "AllArgsConstructor");

    public DomainLayerGenerator(String basePackage) {
        var domainPackage = basePackage + ".domain";
        this.entityPackage = domainPackage + ".entity";
        this.valueObjectPackage = domainPackage + ".valueobject";
        this.commandPackage = domainPackage + ".command";
        this.eventPackage = domainPackage + ".event";
        this.adaptorPackage = domainPackage + ".adaptor";
    }

    private List<TypeSpec> classes = new ArrayList<>();

    public List<TypeSpec> generate(List<Domain> domains) {
        var aggregateCount = domains.stream().filter(Domain::isAggregateRoot).count();
        if (aggregateCount > 1) {
            throw new RuntimeException();
        }

        domains.forEach(domain -> {
            String packageName;
            if (DomainType.ENTITY.equals(domain.getDomainType())) {
                packageName = entityPackage;
            } else {
                packageName = valueObjectPackage;
            }
            if (domain.isAggregateRoot()) {
                aggregateRoot = domain;
                rootName = aggregateRoot.getTypeName();
                uncapitalizedRootName = StringUtils.uncapitalize(rootName);
            }
            domainToPackage.put(domain.getTypeName(), packageName);
        });

        domains.forEach(domain -> {
            var classBuilder = getBaseDomainBuilder(domain);

            var baseFields = new ArrayList<FieldSpec>();
            if (DomainType.ENTITY.equals(domain.getDomainType())) {
                baseFields.add(FieldSpec.builder(UUID.class, StringUtils.uncapitalize(domain.getTypeName())  + "Id", Modifier.PRIVATE).build());
            }
            baseFields.addAll(buildFields(domain.getFields()));

            var methods = new ArrayList<MethodSpec>();
            if (domain.isAggregateRoot()) {
                methods.addAll(buildDomainMethods(domain.getMethods(), baseFields));
                buildAdaptor(domain.getMethods());
            }

            var clazz = buildClass(classBuilder, baseFields, methods);
            classes.add(clazz);
            generateFile(domainToPackage.get(domain.getTypeName()), clazz);
        });
        return classes;
    }

    private void buildAdaptor(List<MethodType> methodTypes) {
        var rootClass = ClassName.get(domainToPackage.get(rootName), rootName);

        var adaptorBuilder = TypeSpec.interfaceBuilder(rootName + "Adaptor")
                .addModifiers(Modifier.PUBLIC);

        if (methodTypes.contains(MethodType.CREATE) || methodTypes.contains(MethodType.UPDATE)) {
            adaptorBuilder.addMethod(MethodSpec.methodBuilder("save")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(rootClass, uncapitalizedRootName)
                    .returns(rootClass)
                    .build());
        }
//        if (methodTypes.contains(MethodType.BATCH_CREATE) || methodTypes.contains(MethodType.BATCH_UPDATE)) {
//            adaptorBuilder.addMethod(MethodSpec.methodBuilder("saveAll")
//                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
//                    .addParameter(ParameterizedTypeName.get(ClassName.get(List.class), rootClass), uncapitalizedRootName + "s")
//                    .returns(ParameterizedTypeName.get(ClassName.get(List.class), rootClass))
//                    .build());
//        }
//        if (methodTypes.contains(MethodType.DELETE)) {
//            adaptorBuilder.addMethod(MethodSpec.methodBuilder("delete")
//                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
//                    .addParameter(UUID.class, uncapitalizedRootName + "Id")
//                    .returns(TypeName.VOID)
//                    .build());
//        }
//        if (methodTypes.contains(MethodType.BATCH_DELETE)) {
//            adaptorBuilder.addMethod(MethodSpec.methodBuilder("deleteAll")
//                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
//                    .addParameter(ParameterizedTypeName.get(ClassName.get(List.class), ClassName.get(UUID.class)), uncapitalizedRootName + "Id")
//                    .returns(TypeName.VOID)
//                    .build());
//        }
        if (methodTypes.contains(MethodType.QUERY_BY_ID)) {
            adaptorBuilder.addMethod(MethodSpec.methodBuilder("findBy" + rootName + "Id")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(UUID.class, uncapitalizedRootName + "Id")
                    .returns(rootClass)
                    .build());
        }
        if (methodTypes.contains(MethodType.QUERY_BY_CRITERIA)) {
            adaptorBuilder.addMethod(MethodSpec.methodBuilder("findAll")
                    .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                    .addParameter(ClassName.get("com.tw.common.criteria", "QuerySchema"), "querySchema")
                    .returns(ParameterizedTypeName.get(ClassName.get(Page.class), rootClass))
                    .build());
        }

        generateFile(adaptorPackage, adaptorBuilder.build());
    }

    private List<MethodSpec> buildDomainMethods(List<MethodType> methods, List<FieldSpec> fields) {
        return methods.stream()
                .filter(methodType -> MethodType.CREATE.equals(methodType) || MethodType.UPDATE.equals(methodType) || MethodType.DELETE.equals(methodType))
                .map(methodType -> {
                    if (MethodType.CREATE.equals(methodType)) {
                        var eventName = rootName + "CreatedEvent";
                        buildEvent(eventName);
                        var commandName = "Create" + rootName + "Command";
                        buildCommand(enhanceCommandFields(fields), commandName);

                        return MethodSpec.methodBuilder("create")
                                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                                .addParameter(ClassName.get(commandPackage, commandName), "command")
                                .returns(ClassName.get(entityPackage, rootName))
                                .addCode(getCreateMethodCode(enhanceDomainFields(fields), eventName))
                                .build();
                    } else if (MethodType.UPDATE.equals(methodType)) {
                        var eventName = rootName + "UpdatedEvent";
                        buildEvent(eventName);
                        var commandName = "Update" + rootName + "Command";
                        buildCommand(fields, commandName);

                        return MethodSpec.methodBuilder("update")
                                .addModifiers(Modifier.PUBLIC)
                                .addParameter(ClassName.get(commandPackage, commandName), "command")
                                .returns(TypeName.VOID)
                                .addCode(getUpdateMethodCode(enhanceDomainFields(fields), eventName))
                                .build();
                    } else {
                        var eventName = rootName + "DeletedEvent";
                        buildEvent(eventName);
                        var commandName = "Delete" + rootName + "Command";
                        buildCommand(List.of(FieldSpec.builder(UUID.class, uncapitalizedRootName + "Id", Modifier.PRIVATE).build()), commandName);

                        return MethodSpec.methodBuilder("delete")
                                .addModifiers(Modifier.PUBLIC)
                                .returns(TypeName.VOID)
                                .addCode(getDeleteMethodCode(eventName))
                                .build();
                    }
                }).toList();
    }

    private CodeBlock getCreateMethodCode(List<FieldSpec> fields, String eventName) {
        var builder = CodeBlock.builder();

        var params = fields.stream()
                .filter(field -> !field.modifiers.contains(Modifier.FINAL))
                .map(field -> "command.get%s()".formatted(WordUtils.capitalize(field.name)))
                .collect(Collectors.joining(", "));
        builder.addStatement("var $L = new $L($L)", uncapitalizedRootName, rootName, params);

        fields.stream()
                .filter(field -> field.type instanceof ParameterizedTypeName parameterizedType
                        && parameterizedType.rawType.equals(ClassName.get(List.class)))
                .forEach(field -> {
                    var capitalizedFieldName = WordUtils.capitalize(field.name);
                    builder.beginControlFlow("if (!$T.isEmpty(command.get$L()))", CollectionUtils.class, capitalizedFieldName)
                            .addStatement("$L.get$L().addAll(command.get$L())", uncapitalizedRootName, capitalizedFieldName, capitalizedFieldName)
                            .endControlFlow();
                });

        builder.addStatement("var event = new $T($L, $L)", ClassName.get(eventPackage, eventName), uncapitalizedRootName, uncapitalizedRootName)
                .addStatement("$L.registerEvent(event)", uncapitalizedRootName)
                .addStatement("return $L", uncapitalizedRootName);

        return builder.build();
    }

    private CodeBlock getUpdateMethodCode(List<FieldSpec> fields, String eventName) {
        var builder = CodeBlock.builder();
        fields.stream()
                .filter(field -> !field.modifiers.contains(Modifier.FINAL))
                .forEach(field -> {
                    builder.beginControlFlow("if (command.get$L() != null)", WordUtils.capitalize(field.name));
                    builder.addStatement("this.$L = command.get$L()", field.name, WordUtils.capitalize(field.name));
                    builder.endControlFlow();
                });

        fields.stream()
                .filter(field -> field.modifiers.contains(Modifier.FINAL))
                .forEach(field -> {
                    builder.addStatement("this.$L.clear()", field.name);
                    builder.addStatement("this.$L.addAll(command.get$L())", field.name, WordUtils.capitalize(field.name));
                });

        builder.addStatement("var event = new $T(this, this)", ClassName.get(eventPackage, eventName))
                .addStatement("registerEvent(event)");

        return builder.build();
    }

    private CodeBlock getDeleteMethodCode(String eventName) {
        return CodeBlock.builder()
                .addStatement("this.deleted = true")
                .addStatement("var event = new $T(this, this)", ClassName.get(eventPackage, eventName))
                .addStatement("registerEvent(event)")
                .build();
    }

    private void buildCommand(List<FieldSpec> fields, String commandName) {
        var command = TypeSpec.classBuilder(commandName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(getterAnnotationClass)
                .addAnnotation(superBuilderAnnotationClass)
                .superclass(ClassName.get(SEEDWORK_PACKAGE, "Command"))
                .addFields(fields)
                .build();

        generateFile(commandPackage, command);
    }

    private List<FieldSpec> enhanceCommandFields(List<FieldSpec> fields) {
        return fields.stream()
                .map(field -> {
                    if (field.name.equals(StringUtils.uncapitalize(rootName + "Id"))) {
                        return field.toBuilder()
                                .addAnnotation(builderDefaultAnnotationClass)
                                .initializer("$T.randomUUID()", UUID.class)
                                .build();
                    }
                    return field;
                })
                .toList();
    }

    private void buildEvent(String eventName) {
        var event = TypeSpec.classBuilder(eventName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(getterAnnotationClass)
                .superclass(ClassName.get(SEEDWORK_PACKAGE, "DomainEvent"))
                .addField(FieldSpec.builder(ClassName.get(entityPackage, rootName), WordUtils.uncapitalize(rootName), Modifier.PRIVATE, Modifier.FINAL).build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(Object.class, "eventSource")
                        .addParameter(ClassName.get(entityPackage, rootName), uncapitalizedRootName)
                        .addCode(CodeBlock.builder()
                                .addStatement("super(eventSource)")
                                .addStatement("this.$L = $L", uncapitalizedRootName, uncapitalizedRootName)
                                .build())
                        .build())
                .build();

        generateFile(eventPackage, event);
    }

    private List<FieldSpec> buildFields(List<Field> fields) {
        return fields.stream()
                .map(field -> {
                    switch (field.getType()) {
                        case OBJECT -> {
                            var referencedClassName = ClassName.get(domainToPackage.get(field.getReferencedType()), field.getReferencedType());
                            return FieldSpec.builder(referencedClassName, field.getName(), Modifier.PRIVATE).build();
                        }
                        case ARRAY -> {
                            var referencedClassName = ClassName.get(domainToPackage.get(field.getReferencedType()), field.getReferencedType());
                            var parameterizedType = ParameterizedTypeName.get(ClassName.get(List.class), referencedClassName);
                            return FieldSpec.builder(parameterizedType, field.getName(), Modifier.PRIVATE).build();
                        }
                        case ENUM -> {
                            var enumType = field.getReferencedType().split("\\.");
                            var referencedClassName = ClassName.get(domainToPackage.get(enumType[0]), enumType[0]).nestedClass(enumType[1]);
                            return FieldSpec.builder(referencedClassName, field.getName(), Modifier.PRIVATE).build();
                        }
                        case INTEGER -> {
                            return FieldSpec.builder(Integer.class, field.getName(), Modifier.PRIVATE).build();
                        }
                        case NUMBER -> {
                            return FieldSpec.builder(Double.class, field.getName(), Modifier.PRIVATE).build();
                        }
                        case STRING -> {
                            return FieldSpec.builder(String.class, field.getName(), Modifier.PRIVATE).build();
                        }
                        case BOOLEAN -> {
                            return FieldSpec.builder(Boolean.class, field.getName(), Modifier.PRIVATE).build();
                        }
                        default -> throw new IllegalArgumentException();
                    }
                })
                .toList();
    }

    private TypeSpec buildClass(TypeSpec.Builder classBuilder, List<FieldSpec> fields, List<MethodSpec> methods) {
        if (!CollectionUtils.isEmpty(fields)) {
            classBuilder.addFields(enhanceDomainFields(fields));
        }
        if (!CollectionUtils.isEmpty(methods)) {
            classBuilder.addMethods(methods);
        }
        return classBuilder.build();
    }

    private List<FieldSpec> enhanceDomainFields(List<FieldSpec> fields) {
        return fields.stream()
                .map(field -> {
                    var enhancedFieldBuilder = field.toBuilder().addModifiers(Modifier.FINAL);
                    var fieldType = field.type;
                    //暂时只考虑给List类型赋初始值
                    if (fieldType instanceof ParameterizedTypeName parameterizedType) {
                        var rawType = parameterizedType.rawType;
                        if (rawType.equals(ClassName.get(List.class))) {
                            return enhancedFieldBuilder.initializer("new $T<>()", ArrayList.class).build();
                        }
                    }
                    return field;
                })
                .toList();
    }

    private TypeSpec.Builder getBaseDomainBuilder(Domain domain) {
        var builder = TypeSpec.classBuilder(domain.getTypeName())
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(getterAnnotationClass)
                .addAnnotation(allArgsConstructorAnnotationClass);

        if (domain.isAggregateRoot()) {
            builder.superclass(ClassName.get(SEEDWORK_PACKAGE, "AbstractAggregateRoot"));
        }

        createInnerEnum(domain, builder);
        return builder;
    }

    private void createInnerEnum(Domain domain, TypeSpec.Builder builder) {
        domain.getFields().stream()
                .filter(field -> FieldType.ENUM.equals(field.getType()))
                .forEach(enumField -> {
                    var enumTypeBuilder = TypeSpec.enumBuilder(enumField.getReferencedType().split("\\.")[1]).addModifiers(Modifier.PUBLIC);
                    for (String enumValue : enumField.getEnumValues()) {
                        enumTypeBuilder.addEnumConstant(convertToSnakeCase(enumValue));
                    }
                    builder.addType(enumTypeBuilder.build());
                });
    }
}
