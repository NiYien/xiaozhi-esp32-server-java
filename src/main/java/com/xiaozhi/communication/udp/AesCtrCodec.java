package com.xiaozhi.communication.udp;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-128-CTR 加解密工具
 * 每包独立加解密（Cipher.init per packet），与 ESP32 mbedtls 完全兼容
 * 16 字节包头同时用作 AES-CTR 的 nonce/IV（双重用途）
 */
public class AesCtrCodec {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CTR/NoPadding";

    /**
     * AES-CTR 加密
     *
     * @param key       AES-128 密钥（16 字节）
     * @param nonce     初始化向量/nonce（16 字节），即 UDP 包头
     * @param plaintext 明文数据
     * @return 密文数据
     */
    public static byte[] encrypt(byte[] key, byte[] nonce, byte[] plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, ALGORITHM),
                    new IvParameterSpec(nonce));
            return cipher.doFinal(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("AES-CTR encrypt failed", e);
        }
    }

    /**
     * AES-CTR 解密
     *
     * @param key        AES-128 密钥（16 字节）
     * @param nonce      初始化向量/nonce（16 字节），即 UDP 包头
     * @param ciphertext 密文数据
     * @return 明文数据
     */
    public static byte[] decrypt(byte[] key, byte[] nonce, byte[] ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, ALGORITHM),
                    new IvParameterSpec(nonce));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("AES-CTR decrypt failed", e);
        }
    }
}
