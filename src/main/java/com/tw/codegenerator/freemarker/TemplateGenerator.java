package com.tw.codegenerator.freemarker;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class TemplateGenerator {

    private final Map<String, String> templateToPath = new HashMap<>();

    public TemplateGenerator() {
        templateToPath.put("Service.java.ftl", "/app/service/");
    }

    public void generate(String aggregateRootName, String templateName) throws IOException {
        var rootPackage = "com.tw.capability";
        var rootPath = rootPackage.replace(".", "/") + "/";
        var filePath = templateToPath.get(templateName);

        var dataModel = new HashMap<String, Object>();
        dataModel.put("rootPackage", rootPackage);
        dataModel.put("aggregateRootName", aggregateRootName);

        // 添加其他需要的变量
        var configuration = new Configuration(Configuration.VERSION_2_3_30);
        configuration.setClassForTemplateLoading(getClass(), "/templates"); // 设置模板文件的路径

        var template = configuration.getTemplate(templateName); // 替换为你的模板文件名
        var stringWriter = new StringWriter();
        try {
            template.process(dataModel, stringWriter);
        } catch (TemplateException | IOException e) {
            e.printStackTrace();
        }
        var generatedCode = stringWriter.toString();

        // 保存生成的代码到文件
        var baseFileName = templateName.substring(0, templateName.lastIndexOf('.'));
        // 指定输出文件路径和名称
        var fileFullPath = "src/main/java/" + rootPath + aggregateRootName.toLowerCase() + filePath + aggregateRootName + baseFileName;

        // 创建文件的目录路径（如果不存在）
        Files.createDirectories(Paths.get(fileFullPath).getParent());

        try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(fileFullPath))) {
            bufferedWriter.write(generatedCode);
            log.info("Generated code saved to " + fileFullPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
