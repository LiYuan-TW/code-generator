package com.tw.codegenerator;

import com.tw.codegenerator.builder.DomainMetadataBuilder;
import com.tw.codegenerator.javapoet.*;
import org.junit.jupiter.api.Test;

class CodeGeneratorTest {

    String basePackage = "com.tw.capability.order";
    String openApiFilePath = "reference/Order.yaml";

    @Test
    void generateAll() throws ClassNotFoundException {
        var domains = new DomainLayerGenerator(basePackage).generate(DomainMetadataBuilder.buildAll());
        new AppLayerGenerator(basePackage).generate(DomainMetadataBuilder.buildOrder(), openApiFilePath);
        new ResourceLayerGenerator(basePackage).generate(openApiFilePath);
        new InfraLayerGeneratorV2(basePackage).generateByTypeSpec(domains);
    }

    @Test
    void generateDomainLayer() {
        var generator = new DomainLayerGenerator("com.tw.capability.order");
        generator.generate(DomainMetadataBuilder.buildAll());
    }

    @Test
    void generateAppLayer() {
        var generator = new AppLayerGenerator("com.tw.capability.order");
        generator.generate(DomainMetadataBuilder.buildOrder(), "Order.yaml");
    }

    @Test
    void generateResourceLayer() {
        var generator = new ResourceLayerGenerator("com.tw.capability.order");
        generator.generate("/Order.yaml");
    }

    @Test
    void generateInfraLayer() throws ClassNotFoundException {
        var generator = new InfraLayerGenerator("com.tw.capability.order");
        generator.generateByDomainMetadata(DomainMetadataBuilder.buildAll());
    }

    @Test
    void generateInfraLayerV2() throws ClassNotFoundException {
        var domains = new DomainLayerGenerator("com.tw.capability.order").generate(DomainMetadataBuilder.buildAll());

        var generator = new InfraLayerGeneratorV2("com.tw.capability.order");
        generator.generateByTypeSpec(domains);
    }
}
