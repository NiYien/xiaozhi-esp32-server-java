package com.xiaozhi.mcp;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * 包装 ToolCallback，将工具名称 sanitize 为 OpenAI 兼容格式 [a-zA-Z0-9_-]{1,64}。
 * 原始中文名称保留在 description 中。
 */
public class SanitizedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolDefinition sanitizedDefinition;

    public SanitizedToolCallback(ToolCallback delegate, String sanitizedName) {
        this.delegate = delegate;
        ToolDefinition original = delegate.getToolDefinition();
        // 将原始名称（可能是中文）附加到 description 中，保留语义信息
        String description = original.description();
        if (description == null || description.isEmpty()) {
            description = original.name();
        } else if (!original.name().equals(sanitizedName)) {
            description = original.name() + " - " + description;
        }
        this.sanitizedDefinition = DefaultToolDefinition.builder()
                .name(sanitizedName)
                .description(description)
                .inputSchema(original.inputSchema())
                .build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return sanitizedDefinition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        // MCP 远程工具不需要本地 ToolContext（含 ChatSession），
        // 传入会导致 ToolContextToMcpMetaConverter 序列化整个对象图触发循环引用
        return delegate.call(toolInput);
    }
}
