package com.tw.codegenerator.javapoet;

import com.tw.codegenerator.metadata.Domain;
import com.tw.common.seedwork.AbstractAggregateRootEntity;
import com.tw.common.seedwork.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.javapoet.*;

import javax.lang.model.element.Modifier;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Collectors;

import static com.tw.codegenerator.utils.GeneratorHelper.generateFile;

public class InfraLayerGenerator extends AbstractInfraLayerGenerator{

    private static final ClassName getterAnnotationClass = ClassName.get("lombok", "Getter");

    private static final ClassName setterAnnotationClass = ClassName.get("lombok", "Setter");

    private final String entityPackage;

    private final String domainPackage;

    private final String domainEntityPackage;

    private List<Domain> domains;

    public InfraLayerGenerator(String basePackage) {
        super(basePackage);
        this.entityPackage = basePackage + ".infra.jpa.entity";
        this.domainPackage = basePackage + ".domain";
        this.domainEntityPackage = domainPackage + ".entity";
    }

    public void generateByDomainMetadata(List<Domain> domains) throws ClassNotFoundException {
        this.domains = domains;

        var root = domains.stream()
            .filter(Domain::isAggregateRoot)
            .findAny()
            .orElseThrow();

        generateEntities(null, root);
        generateMapper(root.getTypeName());
        generateRepository(root.getTypeName());
        generateAdaptorImpl(root.getTypeName());
    }

    private void generateEntities(Domain parent, Domain root) throws ClassNotFoundException {
        var fields = getFields(root);
        if (parent != null) {
            var parentEntityClassName = ClassName.get(entityPackage, parent.getTypeName() + "Entity");
            var parentField = FieldSpec.builder(parentEntityClassName, StringUtils.uncapitalize(parent.getTypeName()))
                .addModifiers(Modifier.PRIVATE)
                .addAnnotation(ManyToOne.class)
                .addAnnotation(AnnotationSpec.builder(JoinColumn.class)
                    .addMember("name", "$S", StringUtils.uncapitalize(parent.getTypeName() + "_id"))
                    .build())
                .build();
            fields.add(parentField);
        }
        var builder = TypeSpec.classBuilder(root.getTypeName() + "Entity")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Entity.class)
            .addAnnotation(getterAnnotationClass)
            .addAnnotation(setterAnnotationClass)
            .addAnnotation(AnnotationSpec.builder(Table.class)
                .addMember("name", "$S", root.getTypeName().toLowerCase())
                .build())
            .addFields(fields);

        if (root.isAggregateRoot()) {
            builder.superclass(AbstractAggregateRootEntity.class);
        } else {
            builder.superclass(BaseEntity.class);
        }

        for (FieldSpec field : fields) {
            if (field.type instanceof ParameterizedTypeName parameterizedTypeName
                && (Collection.class.isAssignableFrom(Class.forName(parameterizedTypeName.rawType.canonicalName())))
                && (!parameterizedTypeName.typeArguments.isEmpty())) {

                var typeArgument = parameterizedTypeName.typeArguments.get(0);
                var entityName = ((ClassName) typeArgument).simpleName();
                if (entityName.endsWith("Entity")) {
                    // 去除"Entity"后缀
                    var domainName = entityName.substring(0, entityName.length() - "Entity".length());

                    if (root.isAggregateRoot()) {
                        builder.addMethod(MethodSpec.methodBuilder("add" + domainName)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(typeArgument, StringUtils.uncapitalize(domainName))
                            .returns(TypeName.VOID)
                            .addCode(CodeBlock.builder()
                                .addStatement("this.$L.add($L)", field.name, StringUtils.uncapitalize(domainName))
                                .addStatement("$L.set$L(this)", StringUtils.uncapitalize(domainName), root.getTypeName())
                                .build())
                            .build()
                        );
                    }

                    domains.stream()
                        .filter(domain -> domainName.equals(domain.getTypeName()))
                        .findAny()
                        .ifPresent(domain -> {
                            try {
                                generateEntities(root, domain);
                            } catch (ClassNotFoundException e) {
                                throw new RuntimeException(e);
                            }
                        });
                }
            }
        }

        var rootEntityClass = builder.build();
        generateFile(entityPackage, rootEntityClass);
    }

    private List<FieldSpec> getFields(Domain root) throws ClassNotFoundException {
        var rootClass = Class.forName(domainEntityPackage + "." + root.getTypeName());
        var fields = rootClass.getDeclaredFields();

        return Arrays.stream(fields)
            .map(field -> {
                var annotations = new ArrayList<AnnotationSpec>();
                var builder = FieldSpec.builder(getFieldType(root, field, annotations), field.getName(), Modifier.PRIVATE);
                builder.addAnnotations(annotations);

                return builder.build();
            })
            .collect(Collectors.toList());
    }

    private TypeName getFieldType(Domain root, Field field, ArrayList<AnnotationSpec> annotations) {
        TypeName fieldType;
        if (field.getGenericType() instanceof ParameterizedType parameterizedType) {
            var rawType = parameterizedType.getRawType();
            if (Collection.class.isAssignableFrom((Class<?>) rawType)) {
                var annotation = AnnotationSpec.builder(OneToMany.class)
                    .addMember("mappedBy", "$S", StringUtils.uncapitalize(root.getTypeName()))
                    .addMember("cascade", "$T.$L", CascadeType.class, "ALL")
                    .addMember("orphanRemoval", "$L", "true")
                    .build();
                annotations.add(annotation);
            }

            var actualTypeArguments = parameterizedType.getActualTypeArguments();
            if (actualTypeArguments.length > 0) {
                var actualType = actualTypeArguments[0];
                var typeName = TypeName.get(rawType);
                var domainClassName = actualType.getTypeName().substring(actualType.getTypeName().lastIndexOf(".") + 1);
                var className = ClassName.get(entityPackage, domainClassName + "Entity");
                fieldType = ParameterizedTypeName.get((ClassName) typeName, className);
            } else {
                throw new IllegalArgumentException("该字段没有实际类型参数。");
            }
        } else {
            if (field.getType().getPackageName().startsWith(domainPackage)) {
                if (field.getType().isEnum()) {
                    var annotation = AnnotationSpec.builder(Enumerated.class)
                        .addMember("value", "$T.$L", EnumType.class, "STRING")
                        .build();
                    annotations.add(annotation);
                } else {
                    var annotation = AnnotationSpec.builder(org.hibernate.annotations.Type.class)
                        .addMember("value", "$T.class", JsonType.class)
                        .build();
                    annotations.add(annotation);
                }
            }

            fieldType = TypeName.get(field.getType());
        }
        return fieldType;
    }
}
