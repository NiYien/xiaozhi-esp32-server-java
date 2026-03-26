package com.xiaozhi.communication.udp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AES-128-CTR 加解密工具测试")
class AesCtrCodecTest {

    @Test
    @DisplayName("加密后解密得到原文")
    void encrypt_decrypt_roundTrip() {
        byte[] key = new byte[16];
        Arrays.fill(key, (byte) 0xAB);
        byte[] nonce = new byte[16];
        Arrays.fill(nonce, (byte) 0xCD);
        byte[] plaintext = "Hello, ESP32 UDP!".getBytes();

        byte[] ciphertext = AesCtrCodec.encrypt(key, nonce, plaintext);
        byte[] decrypted = AesCtrCodec.decrypt(key, nonce, ciphertext);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("已知向量加密 - 与 ESP32 mbedtls AES-CTR nc_off=0 对齐")
    void encrypt_knownVector() {
        // key: 16 字节全 0x00, nonce: 16 字节全 0x00, plaintext: 16 字节全 0x00
        byte[] key = new byte[16];
        byte[] nonce = new byte[16];
        byte[] plaintext = new byte[16];

        byte[] ciphertext = AesCtrCodec.encrypt(key, nonce, plaintext);

        // AES-CTR 对全零 key/nonce/plaintext 的标准输出，第一个字节为 0x66
        assertEquals((byte) 0x66, ciphertext[0],
                "AES-CTR(key=0, nonce=0, plaintext=0) 首字节应为 0x66");
    }

    @Test
    @DisplayName("已知向量解密 - 反向验证")
    void decrypt_knownVector() {
        byte[] key = new byte[16];
        byte[] nonce = new byte[16];
        byte[] plaintext = new byte[16];

        // 先加密获得密文
        byte[] ciphertext = AesCtrCodec.encrypt(key, nonce, plaintext);
        // 解密应得到全零
        byte[] decrypted = AesCtrCodec.decrypt(key, nonce, ciphertext);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    @DisplayName("空数据加密不抛异常，返回空数组")
    void encrypt_emptyPayload() {
        byte[] key = new byte[16];
        byte[] nonce = new byte[16];
        byte[] plaintext = new byte[0];

        byte[] ciphertext = assertDoesNotThrow(() -> AesCtrCodec.encrypt(key, nonce, plaintext));

        assertEquals(0, ciphertext.length);
    }

    @Test
    @DisplayName("相同 key 和 plaintext，不同 nonce 产生不同密文")
    void encrypt_differentNonce_producesDifferentCiphertext() {
        byte[] key = new byte[16];
        Arrays.fill(key, (byte) 0x11);
        byte[] plaintext = new byte[32];
        Arrays.fill(plaintext, (byte) 0xFF);

        byte[] nonce1 = new byte[16];
        Arrays.fill(nonce1, (byte) 0x01);
        byte[] nonce2 = new byte[16];
        Arrays.fill(nonce2, (byte) 0x02);

        byte[] ciphertext1 = AesCtrCodec.encrypt(key, nonce1, plaintext);
        byte[] ciphertext2 = AesCtrCodec.encrypt(key, nonce2, plaintext);

        assertFalse(Arrays.equals(ciphertext1, ciphertext2),
                "不同 nonce 应产生不同密文");
    }

    @Test
    @DisplayName("AES-CTR 不改变数据长度")
    void ctr_noLengthChange() {
        byte[] key = new byte[16];
        byte[] nonce = new byte[16];

        for (int len : new int[]{0, 1, 15, 16, 17, 100, 1024}) {
            byte[] plaintext = new byte[len];
            byte[] ciphertext = AesCtrCodec.encrypt(key, nonce, plaintext);
            assertEquals(len, ciphertext.length,
                    "AES-CTR 密文长度应与明文相同，测试长度: " + len);
        }
    }
}
