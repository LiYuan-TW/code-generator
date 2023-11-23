package com.tw.codegenerator.javapoet;

import com.tw.codegenerator.utils.GeneratorHelper;
import com.tw.codegenerator.utils.OpenApiHelper;
import com.tw.common.seedwork.CommandHandler;
import com.tw.common.seedwork.Query;
import com.tw.common.criteria.Condition;
import com.tw.common.criteria.QuerySchema;
import com.tw.codegenerator.metadata.Domain;
import com.tw.codegenerator.metadata.MethodType;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.parser.OpenAPIV3Parser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.javapoet.*;
import org.springframework.stereotype.Component;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.tw.codegenerator.utils.GeneratorHelper.generateFile;

@Slf4j
public class AppLayerGenerator {

    private final String queryHandlerPackage;

    private final String commandHandlerPackage;

    private final String eventHandlerPackage;

    private final String basePackage;

    private final String dtoPackage;

    private final String queryPackage;

    private final String mapperPackage;

    private final String entityPackage;

    private final String commandPackage;

    private final String eventPackage;

    private final String adaptorPackage;

    private Domain aggregateRoot;

    private String domainClassName;

    private String domainName;

    private ClassName domainClass;

    private ClassName adaptorClass;

    private static final ClassName getterAnnotationClass = ClassName.get("lombok", "Getter");

    private static final ClassName builderAnnotationClass = ClassName.get("lombok", "Builder");

    private static final ClassName requiredArgsConstructorAnnotationClass = ClassName.get("lombok", "RequiredArgsConstructor");

    private String openApiFilePath;

    private OpenAPI openAPI;

    public AppLayerGenerator(String basePackage) {
        this.basePackage = basePackage;
        var appPackage = basePackage + ".app";
        var handlerPackage = appPackage + ".handler";
        this.commandHandlerPackage = handlerPackage + ".command";
        this.queryHandlerPackage = commandHandlerPackage + ".query";
        this.eventHandlerPackage = handlerPackage + ".event";
        this.dtoPackage = appPackage + ".dto";
        this.queryPackage = dtoPackage + ".query";
        this.mapperPackage = appPackage + ".convertor";
        var domainPackage = basePackage + ".domain";
        this.entityPackage = domainPackage + ".entity";
        this.commandPackage = domainPackage + ".command";
        this.eventPackage = domainPackage + ".event";
        this.adaptorPackage = domainPackage + ".adaptor";
    }

    public void generate(Domain aggregateRoot, String openApiFilePath) {
        this.aggregateRoot = aggregateRoot;
        this.domainClassName = aggregateRoot.getTypeName();
        this.domainName = StringUtils.uncapitalize(domainClassName);
        var adaptorClassName = domainClassName + "Adaptor";
        this.domainClass = ClassName.get(entityPackage, domainClassName);
        this.adaptorClass = ClassName.get(adaptorPackage, adaptorClassName);
        this.openApiFilePath = openApiFilePath;

        generateDto();
        generateMapper();
        generateHandlers();
    }


    private void generateHandlers() {
        aggregateRoot.getMethods().forEach(methodType -> {
            switch (methodType) {
                case CREATE -> {
                    generateCommandHandler(methodType, getCreateCodeBlock());
                    generateEventHandler(methodType);
                }
                case UPDATE -> {
                    generateCommandHandler(methodType, getUpdateCodeBlock());
                    generateEventHandler(methodType);
                }
                case DELETE -> {
                    generateCommandHandler(methodType, getDeleteCodeBlock());
                    generateEventHandler(methodType);
                }
                case QUERY_BY_ID -> generateFindByIdQueryHandler();
                case QUERY_BY_CRITERIA -> generateFindByCriteriaQueryHandler();
                default -> throw new IllegalStateException("Unexpected value: " + methodType);
            }
        });
    }

    private void generateFindByIdQueryHandler() {
        generateFindByIdQuery();

        var code = CodeBlock.builder()
            .addStatement("return $T.INSTANCE.to$LDTO(adaptor.findBy$LId(query.get$LId()))",
                ClassName.get(mapperPackage, domainClassName + "AppMapper"), domainClassName, domainClassName, domainClassName)
            .build();

        var condition = "Id";
        var returnType = ClassName.get(dtoPackage, domainClassName + "DTO");
        generateQueryHandler(condition, code, returnType);
    }

    private void generateFindByCriteriaQueryHandler() {
        generateFindByRequestParamQuery();

        var code = CodeBlock.builder()
            .addStatement("var page = adaptor.findAll(query.toQuerySchema())")
            .addStatement("return new $T<>(page.getContent().stream().map($T.INSTANCE::to$LDTO).toList(), page.getPageable(), page.getTotalElements())", PageImpl.class,
                ClassName.get(mapperPackage, domainClassName + "AppMapper"), domainClassName)
            .build();

        var condition = "Criteria";
        var returnType = ParameterizedTypeName.get(ClassName.get(Page.class), ClassName.get(dtoPackage, domainClassName + "DTO"));
        generateQueryHandler(condition, code, returnType);
    }

    private void generateQueryHandler(String condition, CodeBlock code, TypeName returnType) {
        var adaptor = FieldSpec.builder(adaptorClass, "adaptor", Modifier.PRIVATE, Modifier.FINAL).build();
        var param = ParameterSpec.builder(ClassName.get(queryPackage, "Get" + domainClassName + "By" + condition + "Query"), "query").build();
        var method = MethodSpec.methodBuilder("execute")
            .addModifiers(Modifier.PUBLIC)
            .addParameter(param)
            .addCode(code)
            .returns(returnType)
            .build();

        var queryHandler = TypeSpec.classBuilder("Get" + domainClassName + "By" + condition + "QueryHandler")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(requiredArgsConstructorAnnotationClass)
            .addAnnotation(Component.class)
            .addField(adaptor)
            .addMethod(method)
            .build();

        generateFile(queryHandlerPackage, queryHandler);
    }

    private CodeBlock getDeleteCodeBlock() {
        return CodeBlock.builder()
            .addStatement("var $L = adaptor.findBy$LId(command.get$LId())", domainName, domainClassName, domainClassName)
            .addStatement("$L.delete()", domainName)
            .addStatement("return adaptor.save($L)", domainName)
            .build();
    }

    private CodeBlock getUpdateCodeBlock() {
        return CodeBlock.builder()
            .addStatement("var $L = adaptor.findBy$LId(command.get$LId())", domainName, domainClassName, domainClassName)
            .addStatement("$L.update(command)", domainName)
            .addStatement("return adaptor.save($L)", domainName)
            .build();
    }

    private CodeBlock getCreateCodeBlock() {
        return CodeBlock.builder()
            .addStatement("var $L = $T.create(command)", domainName, domainClass)
            .addStatement("return adaptor.save($L)", domainName)
            .build();
    }

    private void generateFindByIdQuery() {
        var operationToParams = openAPI.getPaths().values().stream()
            .map(PathItem::getGet)
            .filter(Objects::nonNull)
            .filter(operation -> operation.getParameters().stream().filter(parameter -> "path".equals(parameter.getIn())).count() == 1)
            .collect(Collectors.toMap(operation -> operation, Operation::getParameters));

        for (Operation operation : operationToParams.keySet()) {
            var fieldName = StringUtils.uncapitalize(operation.getTags().get(0)) + "Id";
            var findByIdQuery = TypeSpec.classBuilder(operation.getSummary() + "Query")
                .addSuperinterface(Query.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(builderAnnotationClass)
                .addAnnotation(getterAnnotationClass)
                .addField(FieldSpec.builder(UUID.class, fieldName, Modifier.PRIVATE, Modifier.FINAL).build())
                .build();

            generateFile(queryPackage, findByIdQuery);
        }

    }

//    private void generateFindByQuerySchemaQuery() {
//        var findByCriteriaQuery = TypeSpec.classBuilder("Get" + domainClassName + "ByQuerySchemaQuery")
//            .addSuperinterface(Query.class)
//            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
//            .addAnnotation(builderAnnotationClass)
//            .addAnnotation(getterAnnotationClass)
//            .addField(FieldSpec.builder(ClassName.get("com.tw.common.criteria", "QuerySchema"), "querySchema", Modifier.PRIVATE, Modifier.FINAL).build())
//            .build();
//        generateFile(queryPackage, findByCriteriaQuery);
//    }

    private void generateFindByRequestParamQuery() {
        var operationToParams = openAPI.getPaths().values().stream()
            .map(PathItem::getGet)
            .filter(Objects::nonNull)
            .filter(operation -> operation.getParameters().stream().filter(parameter -> "query".equals(parameter.getIn())).count() > 1)
            .collect(Collectors.toMap(operation -> operation, Operation::getParameters));

        for (Operation operation : operationToParams.keySet()) {
            var fields = getFields(operation.getParameters());

            var codeBlockBuilder = CodeBlock.builder();
            codeBlockBuilder.addStatement("var conditions = new $T<$T>()", ArrayList.class, Condition.class);
            for (FieldSpec field : fields) {
                codeBlockBuilder.add("if ($L != null) {\n", field.name);
                codeBlockBuilder.addStatement("    conditions.add(new $T(\"$L\", $L))", Condition.class, field.name, field.name);
                codeBlockBuilder.add("}\n", ArrayList.class, Condition.class);
            }
            codeBlockBuilder.addStatement("return new QuerySchema(conditions)", QuerySchema.class);

            var findByCriteriaQuery = TypeSpec.classBuilder(operation.getSummary() + "Query")
                .addSuperinterface(Query.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addAnnotation(builderAnnotationClass)
                .addAnnotation(getterAnnotationClass)
                .addFields(fields)
                .addMethod(MethodSpec.methodBuilder("toQuerySchema")
                    .addModifiers(Modifier.PUBLIC)
                    .addCode(codeBlockBuilder.build())
                    .returns(QuerySchema.class)
                    .build())
                .build();

            generateFile(queryPackage, findByCriteriaQuery);
        }
    }

    private static List<FieldSpec> getFields(List<Parameter> parameters) {
        return parameters.stream()
            .map(parameter -> FieldSpec.builder(GeneratorHelper.getTypeName(parameter.getSchema().getType()),
                parameter.getName(), Modifier.PRIVATE, Modifier.FINAL).build())
            .toList();
    }

    private void generateCommandHandler(MethodType methodType, CodeBlock code) {
        var prefix = StringUtils.capitalize(methodType.name().toLowerCase());
        var commandHandlerClassName = prefix + domainClassName + "CommandHandler";
        var commandClassName = prefix + domainClassName + "Command";
        var commandClass = ClassName.get(commandPackage, commandClassName);
        var interfaceClass = ParameterizedTypeName.get(ClassName.get(CommandHandler.class), domainClass, commandClass);

        var createCommandHandlerClass = TypeSpec.classBuilder(commandHandlerClassName)
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(interfaceClass)
            .addAnnotation(Component.class)
            .addAnnotation(requiredArgsConstructorAnnotationClass)
            .addField(adaptorClass, "adaptor", Modifier.PRIVATE, Modifier.FINAL)
            .addMethod(MethodSpec.methodBuilder("execute")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(commandClass, "command")
                .returns(domainClass)
                .addCode(code)
                .build()
            )
            .build();

        generateFile(commandHandlerPackage, createCommandHandlerClass);
    }

    private void generateEventHandler(MethodType methodType) {
        var prefix = StringUtils.capitalize(methodType.name().toLowerCase() + "d");
        var eventHandlerClassName = domainClassName + prefix + "EventHandler";
        var eventClassName = domainClassName + prefix + "Event";
        var eventClass = ClassName.get(eventPackage, eventClassName);

        var eventCommandHandlerClass = TypeSpec.classBuilder(eventHandlerClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Component.class)
            .addAnnotation(requiredArgsConstructorAnnotationClass)
            .addMethod(MethodSpec.methodBuilder("receive")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(EventListener.class)
                .addParameter(eventClass, "event")
                .returns(TypeName.VOID)
                .build()
            )
            .build();

        generateFile(eventHandlerPackage, eventCommandHandlerClass);
    }

    public void generateDto() {
        this.openAPI = new OpenAPIV3Parser().read(this.openApiFilePath);

        var schemas = openAPI.getComponents().getSchemas();
        var openApiHelper = new OpenApiHelper(basePackage, "DTO");
        openApiHelper.generateBySchemas(schemas);
    }


    private void generateMapper() {
        var mapperClassName = aggregateRoot.getTypeName() + "AppMapper";
        var mapperInstance = FieldSpec.builder(ClassName.get(mapperPackage, mapperClassName), "INSTANCE")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("$T.getMapper($L.class)", ClassName.get("org.mapstruct.factory", "Mappers"), mapperClassName)
            .build();

        var dtoClassName = aggregateRoot.getTypeName() + "DTO";
        var domainToDtoMethodName = "to" + dtoClassName;
        var domainToDtoMethod = MethodSpec.methodBuilder(domainToDtoMethodName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameter(ParameterSpec.builder(domainClass, domainName).build())
            .returns(ClassName.get(dtoPackage, dtoClassName))
            .build();


        var mapperClass = TypeSpec.interfaceBuilder(ClassName.get(mapperPackage, mapperClassName))
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(ClassName.get("org.mapstruct", "Mapper")).build())
            .addField(mapperInstance)
            .addMethod(domainToDtoMethod)
            .build();

        generateFile(mapperPackage, mapperClass);
    }
}
