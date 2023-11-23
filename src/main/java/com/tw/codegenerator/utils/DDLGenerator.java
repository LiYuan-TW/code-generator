package com.tw.codegenerator.utils;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.tool.schema.TargetType;

import java.io.File;
import java.util.EnumSet;

public class DDLGenerator {
    public static void main(String[] args) {
        var configuration = new Configuration();

        // 数据库连接配置
        configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:postgresql://localhost:5432/yuan.li2");
//        configuration.setProperty("hibernate.connection.username", "your-username");
//        configuration.setProperty("hibernate.connection.password", "your-password");
        // 数据库方言
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

        // 构建 Hibernate 配置
        var registry = new StandardServiceRegistryBuilder()
            .applySettings(configuration.getProperties())
            .build();
        var metadataSources = new MetadataSources(registry);

        // 添加多个实体类
//        metadataSources.addAnnotatedClass(HolidayEntity.class);
//        metadataSources.addAnnotatedClass(com.your.package.to.entity.Entity2.class);
        // 添加更多实体类...

        // 创建 Metadata
        var metadata = metadataSources.buildMetadata();

        // 创建 SchemaExport 对象
        var schemaExport = new SchemaExport();
        schemaExport.setFormat(true);  // 格式化输出
        schemaExport.setDelimiter(";"); // 每个语句的分隔符，默认为分号

        // 指定输出脚本的文件路径
        var outputFile = new File("ddl.sql");
        schemaExport.setOutputFile(outputFile.getPath());

        // 生成 SQL 脚本并输出到文件
        schemaExport.create(EnumSet.of(TargetType.SCRIPT), metadata);

        // 关闭注册表
        StandardServiceRegistryBuilder.destroy(registry);
    }
}

