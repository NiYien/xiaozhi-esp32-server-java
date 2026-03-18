package com.xiaozhi.base;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 单元测试基类
 * <p>
 * 提供 Mockito 支持，不启动 Spring 上下文，适用于纯逻辑的单元测试。
 * 使用方式：继承此类，使用 @Mock 和 @InjectMocks 注解。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
public abstract class BaseUnitTest {

}
