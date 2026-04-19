package com.baseorg.docassistant.service.qa.tool;

import com.baseorg.docassistant.dto.qa.tool.DiagramPayload;
import com.baseorg.docassistant.dto.qa.tool.ToolDescriptor;
import com.baseorg.docassistant.dto.qa.tool.ToolExecutionResult;
import com.baseorg.docassistant.dto.qa.tool.ToolStep;

/**
 * 对 {@link ToolExecutionObserver} 的同步包装。
 * <p>
 * 工具执行从串行改为并发后，多个工作线程会并发触发 onToolStart / onToolResult /
 * onToolError / onDiagram 回调，而下游常见 listener（如 WebSocket 流式推送）
 * 写单个 session 并非线程安全。此包装器以内部锁串行化所有回调，保证下游看到的
 * 事件不会并发到达，同时不改变事件内容与顺序（按完成先后）。
 */
final class ThreadSafeToolExecutionObserver implements ToolExecutionObserver {

    private final ToolExecutionObserver delegate;
    private final Object lock = new Object();

    private ThreadSafeToolExecutionObserver(ToolExecutionObserver delegate) {
        this.delegate = delegate;
    }

    static ToolExecutionObserver wrap(ToolExecutionObserver delegate) {
        if (delegate == null) {
            return new ToolExecutionObserver() {
            };
        }
        if (delegate instanceof ThreadSafeToolExecutionObserver) {
            return delegate;
        }
        return new ThreadSafeToolExecutionObserver(delegate);
    }

    @Override
    public void onToolStart(ToolDescriptor descriptor, ToolStep step) {
        synchronized (lock) {
            delegate.onToolStart(descriptor, step);
        }
    }

    @Override
    public void onToolResult(ToolDescriptor descriptor, ToolStep step, ToolExecutionResult result) {
        synchronized (lock) {
            delegate.onToolResult(descriptor, step, result);
        }
    }

    @Override
    public void onToolError(ToolDescriptor descriptor, ToolStep step, String message) {
        synchronized (lock) {
            delegate.onToolError(descriptor, step, message);
        }
    }

    @Override
    public void onDiagram(ToolDescriptor descriptor, ToolStep step, DiagramPayload diagram) {
        synchronized (lock) {
            delegate.onDiagram(descriptor, step, diagram);
        }
    }
}
