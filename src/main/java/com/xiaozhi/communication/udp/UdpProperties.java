package com.xiaozhi.communication.udp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UDP 音频服务配置属性
 * 通过 xiaozhi.udp 前缀绑定配置项
 */
@Data
@ConfigurationProperties(prefix = "xiaozhi.udp")
public class UdpProperties {

    /**
     * 是否启用 UDP 音频服务
     */
    private boolean enabled = false;

    /**
     * UDP 监听端口
     */
    private int port = 8888;

    /**
     * 外部可达 IP（用于 hello 响应中告知设备 UDP 服务地址）
     * Docker 容器内部 IP 设备不可达，需配置宿主机 IP 或公网 IP
     */
    private String externalIp;

    /**
     * UDP 接收缓冲区大小（字节）
     */
    private int bufferSize = 2048;
}
