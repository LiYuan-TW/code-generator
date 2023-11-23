package com.tw.codegenerator.javapoet;

import com.tw.common.criteria.QuerySchema;
import com.tw.common.criteria.SpecificationBuilder;
import jakarta.persistence.EntityNotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.CollectionMappingStrategy;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.javapoet.*;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import javax.lang.model.element.Modifier;
import java.util.Optional;
import java.util.UUID;

import static com.tw.codegenerator.utils.GeneratorHelper.generateFile;

public abstract class AbstractInfraLayerGenerator {

    private static final ClassName requiredArgsConstructorAnnotationClass = ClassName.get("lombok", "RequiredArgsConstructor");

    private final String infraAdaptorPackage;

    private final String mapperPackage;

    private final String entityPackage;

    private final String repositoryPackage;

    private final String domainEntityPackage;

    private final String domainAdaptorPackage;

    protected AbstractInfraLayerGenerator(String basePackage) {
        var infraPackage = basePackage + ".infra";
        this.infraAdaptorPackage = infraPackage + ".adaptor";
        this.mapperPackage = infraPackage + ".convertor";
        var jpaPackage = infraPackage + ".jpa";
        this.entityPackage = jpaPackage + ".entity";
        this.repositoryPackage = jpaPackage + ".repository";
        String domainPackage = basePackage + ".domain";
        this.domainAdaptorPackage = domainPackage + ".adaptor";
        this.domainEntityPackage = domainPackage + ".entity";
    }


    void generateMapper(String domainName) {
        var mapperClassName = domainName + "InfraMapper";

        var mapperInstance = FieldSpec.builder(ClassName.get(mapperPackage, mapperClassName), "INSTANCE")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
            .initializer("$T.getMapper($L.class)", ClassName.get("org.mapstruct.factory", "Mappers"), mapperClassName)
            .build();

        var domainClass = ClassName.get(domainEntityPackage, domainName);
        var entityClass = ClassName.get(entityPackage, domainName + "Entity");

        var domainToEntityMethod = MethodSpec.methodBuilder("to" + domainName + "Entity")
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameter(ParameterSpec.builder(domainClass, StringUtils.uncapitalize(domainName)).build())
            .returns(entityClass)
            .build();

        var entityToDomainMethod = MethodSpec.methodBuilder("to" + domainName)
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameter(ParameterSpec.builder(entityClass, StringUtils.uncapitalize(domainName) + "Entity").build())
            .returns(domainClass)
            .build();

        var mapperClass = TypeSpec.interfaceBuilder(mapperClassName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(AnnotationSpec.builder(Mapper.class)
                .addMember("collectionMappingStrategy", "$T.ADDER_PREFERRED", CollectionMappingStrategy.class)
                .build())
            .addField(mapperInstance)
            .addMethod(domainToEntityMethod)
            .addMethod(entityToDomainMethod)
            .build();

        generateFile(mapperPackage, mapperClass);
    }

    void generateRepository(String domainName) {
        var entityClassName = ClassName.get(entityPackage, domainName + "Entity");

        var findByIdMethod = MethodSpec.methodBuilder("findBy%sId".formatted(domainName))
            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
            .addParameter(ParameterSpec.builder(UUID.class, StringUtils.uncapitalize(domainName) + "Id").build())
            .returns(ParameterizedTypeName.get(ClassName.get(Optional.class), entityClassName))
            .build();

        var repositoryClass = TypeSpec.interfaceBuilder(domainName + "EntityRepository")
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(ParameterizedTypeName.get(ClassName.get(JpaRepositoryImplementation.class), entityClassName, ClassName.get(Long.class)))
            .addAnnotation(Repository.class)
            .addMethod(findByIdMethod)
            .build();

        generateFile(repositoryPackage, repositoryClass);
    }

    void generateAdaptorImpl(String domainName) {
        var uncapitalizedDomainName = StringUtils.uncapitalize(domainName);
        var repositoryClassName = ClassName.get(repositoryPackage, domainName + "EntityRepository");
        var repositoryFieldName = uncapitalizedDomainName + "EntityRepository";
        var domainClassName = ClassName.get(domainEntityPackage, domainName);
        var mapperClassName = ClassName.get(mapperPackage, domainName + "InfraMapper");

        var saveMethod = MethodSpec.methodBuilder("save")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(domainClassName, uncapitalizedDomainName)
            .returns(domainClassName)
            .addCode(CodeBlock.builder()
                .addStatement("return $T.INSTANCE.to$L($LEntityRepository.save($LInfraMapper.INSTANCE.to$LEntity($L)))",
                    mapperClassName,
                    domainName,
                    uncapitalizedDomainName,
                    domainName,
                    domainName,
                    uncapitalizedDomainName
                )
                .build())
            .build();

        var findById = MethodSpec.methodBuilder("findBy%sId".formatted(domainName))
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(UUID.class, uncapitalizedDomainName + "Id")
            .returns(domainClassName)
            .addCode(CodeBlock.builder()
                .addStatement("return $T.INSTANCE.to$L($LEntityRepository.findBy$LId($LId).orElseThrow($T::new))",
                    mapperClassName,
                    domainName,
                    uncapitalizedDomainName,
                    domainName,
                    uncapitalizedDomainName,
                    ClassName.get(EntityNotFoundException.class)
                )
                .build())
            .build();

        var findAllMethod = MethodSpec.methodBuilder("findAll")
            .addAnnotation(Override.class)
            .addModifiers(Modifier.PUBLIC)
            .addParameter(QuerySchema.class, "querySchema")
            .returns(ParameterizedTypeName.get(ClassName.get(Page.class), domainClassName))
            .addCode(CodeBlock.builder()
                .addStatement("var page = $LEntityRepository.findAll($T.build(querySchema), querySchema.getPageable())",
                    uncapitalizedDomainName,
                    SpecificationBuilder.class
                )
                .addStatement("return new $T<>(page.getContent().stream().map($T.INSTANCE::to$L).toList(), page.getPageable(), page.getTotalElements())",
                    PageImpl.class,
                    mapperClassName,
                    domainName
                )
                .build())
            .build();

        var adaptorClass = TypeSpec.classBuilder(domainName + "AdaptorImpl")
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(ClassName.get(domainAdaptorPackage, domainName + "Adaptor"))
            .addAnnotation(Component.class)
            .addAnnotation(requiredArgsConstructorAnnotationClass)
            .addField(FieldSpec.builder(repositoryClassName, repositoryFieldName).addModifiers(Modifier.PRIVATE, Modifier.FINAL).build())
            .addMethod(saveMethod)
            .addMethod(findById)
            .addMethod(findAllMethod)
            .build();

        generateFile(infraAdaptorPackage, adaptorClass);
    }
}
