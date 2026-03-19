package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户长期记忆实体
 * 记忆按 userId 存储，跨设备、跨角色共享
 */
@Data
@Accessors(chain = true)
@Schema(description = "用户长期记忆")
public class SysUserMemory implements Serializable {

    @Schema(description = "记忆ID")
    private Long memoryId;

    @Schema(description = "用户ID")
    private Integer userId;

    @Schema(description = "记忆分类：preference-偏好，fact-事实，habit-习惯，relationship-关系，other-其他")
    private String category;

    @Schema(description = "记忆内容")
    private String content;

    @Schema(description = "状态：1-有效，0-删除")
    private String state;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "创建时间")
    private Date createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "更新时间")
    private Date updateTime;
}
