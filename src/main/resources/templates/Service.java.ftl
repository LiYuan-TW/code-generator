<#assign lowerAggregateRootName = aggregateRootName?uncap_first>
<#assign dtoPackage = "com.tw.capability.${lowerAggregateRootName}.app.dto">
<#assign commandPackage = "com.tw.capability.${lowerAggregateRootName}.domain.command">
<#assign queryPackage = "com.tw.capability.${lowerAggregateRootName}.app.dto.query">
<#assign pagePackage = "org.springframework.data.domain">
package ${rootPackage}.${lowerAggregateRootName}.app.service;

import ${commandPackage}.Create${aggregateRootName}Command;
import ${commandPackage}.Update${aggregateRootName}Command;
import ${queryPackage}.Find${aggregateRootName}ByCriteriaQuery;
import ${queryPackage}.Find${aggregateRootName}ByIdQuery;
import ${dtoPackage}.${aggregateRootName}DTO;
import ${pagePackage}.Page;

import java.util.UUID;

public interface ${aggregateRootName}Service {

    UUID create${aggregateRootName}(Create${aggregateRootName}Command command);

    void update${aggregateRootName}(Update${aggregateRootName}Command command);

    ${aggregateRootName}DTO getById(Find${aggregateRootName}ByIdQuery query);

    Page<${aggregateRootName}DTO> getByCriteria(Find${aggregateRootName}ByCriteriaQuery query);

}