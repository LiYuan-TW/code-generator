<#assign lowerAggregateRootName = aggregateRootName?uncap_first>
<#assign packageName = "com.tw.capability.order.app.handler.command">
<#assign commandHandlerName = "CreateOrderCommandHandler">
<#assign commandType = "CreateOrderCommand">
<#assign entityType = "Order">
<#assign adaptorType = "OrderAdaptor">

package ${packageName};

import com.tw.common.seedwork.CommandHandler;
import ${packageName}.${entityType}.${adaptorType};
import ${packageName}.${commandType};
import ${packageName}.${entityType}.${entityType};
import lombok.RequiredArgsConstructor;asdwasd
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ${commandHandlerName} implements CommandHandler<${entityType}, ${commandType}> {
    private final ${adaptorType} adaptor;

    @Override
    public ${entityType} execute(${commandType} command) {
        var ${lowerAggregateRootName} = ${entityType}.create(command);
        return adaptor.save(${lowerAggregateRootName});
    }
}
