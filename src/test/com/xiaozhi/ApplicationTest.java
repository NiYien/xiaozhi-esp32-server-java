package com.xiaozhi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 应用基础冒烟测试 - 验证主类和测试框架正常工作
 */
class ApplicationTest {

    @Test
    void applicationClassExists() {
        assertDoesNotThrow(() -> {
            Class<?> clazz = Class.forName("com.xiaozhi.XiaozhiApplication");
            assertNotNull(clazz, "XiaozhiApplication 类应存在");
        });
    }

    @Test
    void testFrameworkWorks() {
        // 验证 JUnit 5 测试框架正常运行
        assertNotNull("测试框架工作正常");
    }
}
