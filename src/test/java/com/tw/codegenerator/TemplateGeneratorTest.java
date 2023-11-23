package com.tw.codegenerator;

import com.tw.codegenerator.freemarker.TemplateGenerator;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class TemplateGeneratorTest {

    @Test
    void generate() throws IOException {
        var templateGenerator = new TemplateGenerator();
        templateGenerator.generate("Order", "Service.java.ftl");
    }
}