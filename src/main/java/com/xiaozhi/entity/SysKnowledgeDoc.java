package com.xiaozhi.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 知识库文档实体类
 *
 * @author xiaozhi
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"startTime", "endTime", "start", "limit"})
@Schema(description = "知识库文档信息")
public class SysKnowledgeDoc extends Base<SysKnowledgeDoc> {

    /**
     * 文档ID
     */
    @Schema(description = "文档ID")
    private Long docId;

    /**
     * 所属知识库ID
     */
    @Schema(description = "所属知识库ID")
    private Long knowledgeBaseId;

    /**
     * 文档名称（原始文件名）
     */
    @Schema(description = "文档名称")
    private String docName;

    /**
     * 文档类型：txt, pdf, md, docx
     */
    @Schema(description = "文档类型")
    private String docType;

    /**
     * 原始文件存储路径
     */
    @Schema(description = "文件存储路径")
    private String filePath;

    /**
     * 文件大小（字节）
     */
    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    /**
     * 分块数量
     */
    @Schema(description = "分块数量")
    private Integer chunkCount;

    /**
     * 总字符数
     */
    @Schema(description = "总字符数")
    private Integer charCount;

    /**
     * 处理状态：uploading-上传中, processing-处理中, ready-就绪, failed-失败
     */
    @Schema(description = "处理状态")
    private String status;

    /**
     * 处理失败时的错误信息
     */
    @Schema(description = "错误信息")
    private String errorMsg;

    /**
     * 状态：1-有效，0-删除
     */
    @Schema(description = "状态")
    private String state;
}
