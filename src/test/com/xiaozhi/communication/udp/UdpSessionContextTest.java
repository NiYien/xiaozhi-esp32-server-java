package com.xiaozhi.communication.udp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UDP 会话上下文测试")
class UdpSessionContextTest {

    private UdpSessionContext createContext() {
        return new UdpSessionContext("test-session", 12345, new byte[16], new byte[16]);
    }

    @Test
    @DisplayName("本地序列号从 1 开始并递增")
    void nextLocalSequence_startsAt1_andIncrements() {
        UdpSessionContext ctx = createContext();

        assertEquals(1, ctx.nextLocalSequence());
        assertEquals(2, ctx.nextLocalSequence());
        assertEquals(3, ctx.nextLocalSequence());
    }

    @Test
    @DisplayName("连续序列号返回 1（有效连续包）")
    void validateRemoteSequence_validSequence_returns1() {
        UdpSessionContext ctx = createContext();

        // remoteSequence 初始为 0，收到 1 是连续的
        assertEquals(1, ctx.validateRemoteSequence(1));
        // 收到 2 也是连续的
        assertEquals(1, ctx.validateRemoteSequence(2));
        assertEquals(1, ctx.validateRemoteSequence(3));
    }

    @Test
    @DisplayName("旧序列号返回 0（应丢弃）")
    void validateRemoteSequence_oldPacket_returns0() {
        UdpSessionContext ctx = createContext();

        // 先推进到 5
        ctx.validateRemoteSequence(5);

        // 旧包：序列号 3 <= 当前 5
        assertEquals(0, ctx.validateRemoteSequence(3));
        // 重复包：序列号 5 <= 当前 5
        assertEquals(0, ctx.validateRemoteSequence(5));
    }

    @Test
    @DisplayName("跳跃序列号返回 2（非连续有效包）")
    void validateRemoteSequence_jumpPacket_returns2() {
        UdpSessionContext ctx = createContext();

        // remoteSequence 初始为 0，收到 1 是连续
        assertEquals(1, ctx.validateRemoteSequence(1));
        // 跳到 5，不是 1+1=2，返回 2
        assertEquals(2, ctx.validateRemoteSequence(5));
    }

    @Test
    @DisplayName("无符号比较边界 - Integer.MAX_VALUE 到 MAX_VALUE+1 的溢出")
    void validateRemoteSequence_unsignedComparison() {
        UdpSessionContext ctx = createContext();

        // 推进到 Integer.MAX_VALUE
        ctx.validateRemoteSequence(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, ctx.getRemoteSequence());

        // Integer.MAX_VALUE + 1 在有符号视为负数，但无符号比较应大于 MAX_VALUE
        int next = Integer.MAX_VALUE + 1; // 0x80000000，即 Integer.MIN_VALUE
        int result = ctx.validateRemoteSequence(next);
        assertTrue(result > 0, "无符号比较下 0x80000000 > 0x7FFFFFFF，应返回非 0");
    }

    @Test
    @DisplayName("停用后 isActive 返回 false")
    void deactivate_setsInactiveFalse() {
        UdpSessionContext ctx = createContext();

        assertTrue(ctx.isActive());
        ctx.deactivate();
        assertFalse(ctx.isActive());
    }

    @Test
    @DisplayName("初始状态验证：active=true, remoteSequence=0")
    void initialState() {
        UdpSessionContext ctx = createContext();

        assertTrue(ctx.isActive());
        assertEquals(0, ctx.getRemoteSequence());
        assertEquals("test-session", ctx.getSessionId());
        assertEquals(12345, ctx.getSsrc());
    }
}
