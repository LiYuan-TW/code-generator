package com.tw.codegenerator.javapoet;

import com.tw.common.seedwork.AbstractAggregateRoot;
import com.tw.common.seedwork.AbstractAggregateRootEntity;
import com.tw.common.seedwork.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import org.apache.commons.lang3.StringUtils;
import org.springframework.javapoet.*;

import javax.lang.model.element.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import static com.tw.codegenerator.utils.GeneratorHelper.generateFile;

public class InfraLayerGeneratorV2 extends AbstractInfraLayerGenerator {

    private static final ClassName getterAnnotationClass = ClassName.get("lombok", "Getter");

    private static final ClassName setterAnnotationClass = ClassName.get("lombok", "Setter");

    private final String entityPackage;

    private final String domainPackage;

    private List<TypeSpec> domains;

    public InfraLayerGeneratorV2(String basePackage) {
        super(basePackage);
        this.entityPackage = basePackage + ".infra.jpa.entity";
        this.domainPackage = basePackage + ".domain";
    }

    public void generateByTypeSpec(List<TypeSpec> domains) throws ClassNotFoundException {
        this.domains = domains;

        var root = domains.stream()
            .filter(this::isAggregateRoot)
            .findAny()
            .orElseThrow();

        generateEntities(null, root);
        generateMapper(root.name);
        generateRepository(root.name);
        generateAdaptorImpl(root.name);
    }

    private boolean isAggregateRoot(TypeSpec domain) {
        return domain.superclass.equals(ClassName.get(AbstractAggregateRoot.class));
    }

    private void generateEntities(TypeSpec parent, TypeSpec root) throws ClassNotFoundException {
        var fields = getFields(root);
        if (parent != null) {
            var parentEntityClassName = ClassName.get(entityPackage, parent.name + "Entity");
            var parentField = FieldSpec.builder(parentEntityClassName, StringUtils.uncapitalize(parent.name))
                .addModifiers(Modifier.PRIVATE)
                .addAnnotation(ManyToOne.class)
                .addAnnotation(AnnotationSpec.builder(JoinColumn.class)
                    .addMember("name", "$S", StringUtils.uncapitalize(parent.name + "_id"))
                    .build())
                .build();
            fields.add(parentField);
        }
        var builder = TypeSpec.classBuilder(root.name + "Entity")
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Entity.class)
            .addAnnotation(getterAnnotationClass)
            .addAnnotation(setterAnnotationClass)
            .addAnnotation(AnnotationSpec.builder(Table.class)
                .addMember("name", "$S", root.name.toLowerCase())
                .build())
            .addFields(fields);

        if (isAggregateRoot(root)) {
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

                    if (isAggregateRoot(root)) {
                        builder.addMethod(MethodSpec.methodBuilder("add" + domainName)
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(typeArgument, StringUtils.uncapitalize(domainName))
                            .returns(TypeName.VOID)
                            .addCode(CodeBlock.builder()
                                .addStatement("this.$L.add($L)", field.name, StringUtils.uncapitalize(domainName))
                                .addStatement("$L.set$L(this)", StringUtils.uncapitalize(domainName), root.name)
                                .build())
                            .build()
                        );
                    }

                    domains.stream()
                        .filter(domain -> domainName.equals(domain.name))
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

    private List<FieldSpec> getFields(TypeSpec root) {
        return root.fieldSpecs.stream()
            .map(field -> {
                var annotations = new ArrayList<AnnotationSpec>();
                FieldSpec.Builder builder;
                try {
                    builder = FieldSpec.builder(getFieldType(root, field, annotations), field.name, Modifier.PRIVATE);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
                builder.addAnnotations(annotations);

                return builder.build();
            })
            .collect(Collectors.toList());
    }

    private TypeName getFieldType(TypeSpec root, FieldSpec field, ArrayList<AnnotationSpec> annotations) throws ClassNotFoundException {
        var fieldType = field.type;
        if (fieldType instanceof ParameterizedTypeName parameterizedTypeName) {
            var rawType = parameterizedTypeName.rawType;
            if (Collection.class.isAssignableFrom(Class.forName(rawType.canonicalName()))) {
                var annotation = AnnotationSpec.builder(OneToMany.class)
                    .addMember("mappedBy", "$S", StringUtils.uncapitalize(root.name))
                    .addMember("cascade", "$T.$L", CascadeType.class, "ALL")
                    .addMember("orphanRemoval", "$L", "true")
                    .build();
                annotations.add(annotation);
            }

            var actualTypeArguments = parameterizedTypeName.typeArguments;
            if (!actualTypeArguments.isEmpty()) {
                var className = ((ClassName) actualTypeArguments.get(0)).simpleName() + "Entity";
                fieldType = ParameterizedTypeName.get(rawType, ClassName.get(entityPackage, className));
            } else {
                throw new IllegalArgumentException("该字段没有实际类型参数。");
            }
        } else {
            if (fieldType.toString().startsWith(domainPackage)) {
                var className = (ClassName) field.type;
                root.typeSpecs.stream()
                    .filter(typeSpec -> typeSpec.kind.name().equals("ENUM"))
                    .filter(typeSpec -> typeSpec.name.equals(className.simpleName()))
                    .findAny()
                    .ifPresentOrElse(typeSpec -> {
                        var annotation = AnnotationSpec.builder(Enumerated.class)
                            .addMember("value", "$T.$L", EnumType.class, "STRING")
                            .build();
                        annotations.add(annotation);
                    }, () -> {
                        var annotation = AnnotationSpec.builder(org.hibernate.annotations.Type.class)
                            .addMember("value", "$T.class", JsonType.class)
                            .build();
                        annotations.add(annotation);
                    });
            }
        }
        return fieldType;
    }
}
