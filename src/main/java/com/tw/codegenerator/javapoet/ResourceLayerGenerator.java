package com.tw.codegenerator.javapoet;

import com.tw.codegenerator.utils.OpenApiHelper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.javapoet.*;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static com.tw.codegenerator.utils.GeneratorHelper.generateFile;
import static com.tw.codegenerator.utils.GeneratorHelper.getTypeName;
import static com.tw.codegenerator.utils.OpenApiHelper.getRefClassName;
import static com.tw.codegenerator.utils.OpenApiHelper.getRefTypeName;

@Slf4j
public class ResourceLayerGenerator {

    // Tag : URL - HttpMethod - Operation
    private final Map<String, List<Triple<String, PathItem.HttpMethod, Operation>>> controllerGroup = new HashMap<>();

    private final Map<String, List<Triple<String, PathItem.HttpMethod, Operation>>> queryControllerGroup = new HashMap<>();

    private static final ClassName requiredArgsConstructorAnnotationClass = ClassName.get("lombok", "RequiredArgsConstructor");

    private final String basePackage;

    private final String controllerPackage;

    private final String responsePackage;

    private final String queryHandlerPackage;

    private final String commandHandlerPackage;

    private final String mapperPackage;

    private final String dtoPackage;

    private final String queryPackage;

    private final String commandPackage;

    public ResourceLayerGenerator(String basePackage) {
        this.basePackage = basePackage;
        var resourcePackage = basePackage + ".resource";
        this.controllerPackage = resourcePackage + ".controller";
        this.responsePackage = controllerPackage + ".response";
        this.commandHandlerPackage = basePackage + ".app.handler.command";
        this.queryHandlerPackage = commandHandlerPackage + ".query";
        this.mapperPackage = resourcePackage + ".convertor";
        this.dtoPackage = basePackage + ".app.dto";
        this.queryPackage = dtoPackage + ".query";
        this.commandPackage = basePackage + ".domain.command";
    }


    public void generate(String openApiFilePath) {
        var openAPI = new OpenAPIV3Parser().read(openApiFilePath);

        groupMethods(openAPI);

        generateRequestBodies(openAPI.getComponents().getRequestBodies());
        generateResponses();
        generateMappers();
        generateControllers();
    }

    private void generateRequestBodies(Map<String, RequestBody> requestBodies) {
        var openApiHelper = new OpenApiHelper(basePackage, "");
        openApiHelper.generateByRequestBodies(requestBodies);
    }

    private void generateResponses() {
        var mergedSet = new HashSet<>(queryControllerGroup.values());
        mergedSet.addAll(controllerGroup.values());

        var operations = mergedSet.stream()
            .flatMap(List::stream)
            .map(Triple::getRight)
            .filter(operation -> Optional.of(operation)
                .map(Operation::getResponses)
                .map(apiResponses -> apiResponses.get("200"))
                .map(ApiResponse::getContent)
                .map(content -> content.get("application/json"))
                .map(MediaType::getSchema)
                .isPresent())
            .collect(Collectors.toMap(
                operation -> StringUtils.capitalize(operation.getSummary()),
                Operation::getResponses));
        var openApiHelper = new OpenApiHelper(basePackage, "");
        openApiHelper.generateByResponses(operations);
    }

    private void generateMappers() {
        for (String domainName : queryControllerGroup.keySet()) {
            var mapperClassName = domainName + "RepresentationMapper";
            var mapperInstance = FieldSpec.builder(ClassName.get(mapperPackage, mapperClassName), "INSTANCE")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$T.getMapper($L.class)", ClassName.get("org.mapstruct.factory", "Mappers"), mapperClassName)
                .build();


            var methods = new ArrayList<MethodSpec>();

            var triples = queryControllerGroup.get(domainName);
            triples.stream()
                .map(Triple::getRight)
                .forEach(operation -> {
                    var parameters = operation.getParameters();
                    var pathParams = parameters.stream().filter(parameter -> parameter.getIn().equals("path")).toList();
                    var responseClassName = operation.getSummary() + "Response";

                    if (pathParams.size() == 1) {
                        var dtoClass = ClassName.get(dtoPackage, domainName + "DTO");
                        var dtoToResponseMethod = MethodSpec.methodBuilder("to" + responseClassName)
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .addParameter(ParameterSpec.builder(dtoClass, domainName.toLowerCase() + "Dto").build())
                            .returns(ClassName.get(responsePackage, responseClassName))
                            .build();
                        methods.add(dtoToResponseMethod);
                    }

                    var queryParams = parameters.stream().filter(parameter -> parameter.getIn().equals("query")).toList();
                    if (!CollectionUtils.isEmpty(queryParams)) {
                        var dtoPageClass = ParameterizedTypeName.get(ClassName.get(Page.class), ClassName.get(dtoPackage, domainName + "DTO"));
                        var dtoToPageResponseMethod = MethodSpec.methodBuilder("to" + responseClassName)
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .addParameter(ParameterSpec.builder(dtoPageClass, domainName.toLowerCase() + "DtoPage").build())
                            .returns(ClassName.get(responsePackage, responseClassName))
                            .build();
                        methods.add(dtoToPageResponseMethod);
                    }
                });

            var mapperClass = TypeSpec.interfaceBuilder(ClassName.get(mapperPackage, mapperClassName))
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(ClassName.get("org.mapstruct", "Mapper")).build())
                .addField(mapperInstance)
                .addMethods(methods)
                .build();

            generateFile(mapperPackage, mapperClass);

        }

    }

    private void generateControllers(Map<String, List<Triple<String, PathItem.HttpMethod, Operation>>> controllerGroup, String suffix) {
        for (String tag : controllerGroup.keySet()) {
            var controllerClassName = Arrays.stream(tag.split(" "))
                .map(StringUtils::capitalize)
                .collect(Collectors.joining()) + suffix;

            var operations = controllerGroup.get(tag);
            var fields = getControllerFields(operations);

            var controllerClass = TypeSpec.classBuilder(controllerClassName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(RestController.class)
                .addAnnotation(requiredArgsConstructorAnnotationClass)
                .addFields(fields)
                .addMethods(getControllerMethods(operations))
                .build();

            generateFile(controllerPackage, controllerClass);
        }
    }

    private List<FieldSpec> getControllerFields(List<Triple<String, PathItem.HttpMethod, Operation>> triples) {
        return triples.stream()
            .map(triple -> {
                var httpMethod = triple.getMiddle();
                var operation = triple.getRight();
                var domainName = operation.getTags().get(0);

                return switch (httpMethod) {
                    case GET -> {
                        var parameters = operation.getParameters();
                        if (parameters.isEmpty()) {
                            throw new RuntimeException();
                        }

                        var pathParams = operation.getParameters().stream().map(Parameter::getIn).filter(in -> in.equals("path")).toList();
                        var queryParams = operation.getParameters().stream().map(Parameter::getIn).filter(in -> in.equals("query")).toList();
                        if (pathParams.size() == 1) {
                            yield getFieldSpec(operation.getSummary() + "QueryHandler", queryHandlerPackage);
                        }
                        if (pathParams.size() > 1 || !queryParams.isEmpty()) {
                            yield getFieldSpec(operation.getSummary() + "QueryHandler", queryHandlerPackage);
                        }
                        throw new RuntimeException();
                    }
                    case POST -> getFieldSpec("Create" + domainName + "CommandHandler", commandHandlerPackage);
                    case PATCH -> getFieldSpec("Update" + domainName + "CommandHandler", commandHandlerPackage);
                    case DELETE -> getFieldSpec("Delete" + domainName + "CommandHandler", commandHandlerPackage);
                    default -> throw new RuntimeException();
                };
            }).distinct()
            .toList();
    }

    private FieldSpec getFieldSpec(String className, String packageName) {
        return FieldSpec.builder(ClassName.get(packageName, className), StringUtils.uncapitalize(className), Modifier.PRIVATE, Modifier.FINAL).build();
    }

    private void generateControllers() {
        generateControllers(queryControllerGroup, "QueryController");
        generateControllers(controllerGroup, "Controller");
    }

    private List<MethodSpec> getControllerMethods(List<Triple<String, PathItem.HttpMethod, Operation>> operations) {
        return operations.stream()
            .map(triple -> {
                var method = triple.getMiddle();
                var operation = triple.getRight();
                var getMappingAnnotation = getAnnotationSpec(triple, method);

                var builder = MethodSpec.methodBuilder(StringUtils.uncapitalize(operation.getSummary()))
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(getMappingAnnotation)
                    .addCode(getCodeBlock(method, operation));

                var responseSchemaOptional = Optional.of(operation)
                    .map(Operation::getResponses)
                    .map(apiResponses -> apiResponses.get("200"))
                    .map(ApiResponse::getContent)
                    .map(content -> content.get("application/json"))
                    .map(MediaType::getSchema);

                var returnType =
                    responseSchemaOptional.isEmpty() ? ClassName.get(Void.class) : ClassName.get(responsePackage, StringUtils.capitalize(operation.getSummary()) + "Response");
                builder.returns(ParameterizedTypeName.get(ClassName.get(ResponseEntity.class), returnType));

                var parameters = getParameters(operation);
                if (!CollectionUtils.isEmpty(parameters)) {
                    builder.addParameters(parameters);
                }

                return builder.build();
            })
            .toList();
    }

    private ArrayList<ParameterSpec> getParameters(Operation operation) {
        var parameters = new ArrayList<ParameterSpec>();
        if (!CollectionUtils.isEmpty(operation.getParameters())) {
            parameters.addAll(getParameters(operation.getParameters()));
        }
        if (operation.getRequestBody() != null) {
            parameters.add(getRequestBodyParam(operation.getRequestBody()));
        }
        return parameters;
    }

    private CodeBlock getCodeBlock(PathItem.HttpMethod method, Operation operation) {
        var domainName = operation.getTags().get(0);
        return switch (method) {
            case GET -> {
                var parameters = operation.getParameters();
                if (parameters.isEmpty()) {
                    throw new RuntimeException();
                }

                var pathParams = operation.getParameters().stream().map(Parameter::getIn).filter("path"::equals).toList();
                var queryParams = operation.getParameters().stream().map(Parameter::getIn).filter("query"::equals).toList();

                if (pathParams.size() == 1) {
                    yield CodeBlock.builder()
                        .addStatement("var query = $T.builder().build()", ClassName.get(queryPackage, operation.getSummary() + "Query"))
                        .addStatement("var dto = $LQueryHandler.execute(query)", StringUtils.uncapitalize(operation.getSummary()))
                        .addStatement("var response = $T.INSTANCE.to$LResponse(dto)", ClassName.get(mapperPackage, domainName + "RepresentationMapper"), operation.getSummary())
                        .addStatement("return $T.ok(response)", ResponseEntity.class)
                        .build();
                }
                if (pathParams.size() > 1 || !queryParams.isEmpty()) {
                    yield CodeBlock.builder()
                        .addStatement("var query = $T.builder().build()", ClassName.get(queryPackage, operation.getSummary() + "Query"))
                        .addStatement("var dtoPage = $LQueryHandler.execute(query)", StringUtils.uncapitalize(operation.getSummary()))
                        .addStatement("var response = $T.INSTANCE.to$LResponse(dtoPage)", ClassName.get(mapperPackage, domainName + "RepresentationMapper"), operation.getSummary())
                        .addStatement("return $T.ok(response)", ResponseEntity.class)
                        .build();
                }
                throw new RuntimeException();
            }
            case POST, PATCH, DELETE -> CodeBlock.builder()
                .addStatement("var command = $T.builder().build()", ClassName.get(commandPackage, operation.getSummary() + "Command"))
                .addStatement("$L.execute(command)", StringUtils.uncapitalize(operation.getSummary()) + "CommandHandler")
                .addStatement("return $T.ok().build()", ResponseEntity.class)
                .build();
            default -> throw new RuntimeException();
        };
    }

    private static AnnotationSpec getAnnotationSpec(Triple<String, PathItem.HttpMethod, Operation> urlToMethodToOperation, PathItem.HttpMethod method) {
        return switch (method) {
            case POST -> AnnotationSpec.builder(PostMapping.class)
                .addMember("value", "$S", urlToMethodToOperation.getLeft())
                .build();
            case GET -> AnnotationSpec.builder(GetMapping.class)
                .addMember("value", "$S", urlToMethodToOperation.getLeft())
                .build();
            case PUT -> AnnotationSpec.builder(PutMapping.class)
                .addMember("value", "$S", urlToMethodToOperation.getLeft())
                .build();
            case PATCH -> AnnotationSpec.builder(PatchMapping.class)
                .addMember("value", "$S", urlToMethodToOperation.getLeft())
                .build();
            case DELETE -> AnnotationSpec.builder(DeleteMapping.class)
                .addMember("value", "$S", urlToMethodToOperation.getLeft())
                .build();
            case HEAD, OPTIONS, TRACE -> throw new IllegalArgumentException();
        };
    }

    private void groupMethods(OpenAPI openAPI) {
        for (Map.Entry<String, PathItem> urlPathItemEntry : openAPI.getPaths().entrySet()) {
            var url = urlPathItemEntry.getKey();
            var pathItem = urlPathItemEntry.getValue();

            handlePathItem(pathItem.getGet(), queryControllerGroup, url, "GET");
            handlePathItem(pathItem.getPost(), controllerGroup, url, "POST");
            handlePathItem(pathItem.getPut(), controllerGroup, url, "PUT");
            handlePathItem(pathItem.getPatch(), controllerGroup, url, "PATCH");
            handlePathItem(pathItem.getDelete(), controllerGroup, url, "DELETE");
        }
    }

    private void handlePathItem(Operation pathItem, Map<String, List<Triple<String, PathItem.HttpMethod, Operation>>> queryControllerGroup, String url, String httpMethod) {
        if (pathItem != null) {
            var methods = queryControllerGroup.get(pathItem.getTags().get(0));
            if (methods == null) {
                queryControllerGroup.put(pathItem.getTags().get(0), newArrayList(Triple.of(url, PathItem.HttpMethod.valueOf(httpMethod), pathItem)));
            } else {
                methods.add(Triple.of(url, PathItem.HttpMethod.valueOf(httpMethod), pathItem));
            }
        }
    }

    private List<ParameterSpec> getParameters(List<Parameter> swaggerParameters) {
        return swaggerParameters.stream()
            .map(swaggerParameter -> {
                var parameterSchema = swaggerParameter.getSchema();

                var parameterBuilder = ParameterSpec.builder(
                    getTypeName(parameterSchema.getType()),
                    swaggerParameter.getName()
                );

                if (swaggerParameter.getIn().equalsIgnoreCase("path")) {
                    parameterBuilder.addAnnotation(AnnotationSpec.builder(PathVariable.class)
                        .addMember("value", "$S", swaggerParameter.getName())
                        .build());
                } else if (swaggerParameter.getIn().equalsIgnoreCase("query")) {
                    parameterBuilder.addAnnotation(AnnotationSpec.builder(RequestParam.class)
                        .addMember("value", "$S", swaggerParameter.getName())
                        .build());
                }
                return parameterBuilder.build();
            })
            .collect(Collectors.toList());
    }

    private ParameterSpec getRequestBodyParam(RequestBody requestBody) {
        var ref = requestBody.get$ref();
        if (ref != null) {
            return ParameterSpec.builder(getRefTypeName(ref, basePackage), StringUtils.uncapitalize(getRefClassName(ref)))
                .addAnnotation(AnnotationSpec.builder(RequestParam.class).build())
                .build();
        }
        return null;
    }
}
