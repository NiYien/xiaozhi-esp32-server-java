package com.xiaozhi.controller;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.dialogue.rag.DocumentProcessingService;
import com.xiaozhi.entity.SysKnowledgeBase;
import com.xiaozhi.entity.SysKnowledgeDoc;
import com.xiaozhi.service.SysKnowledgeBaseService;
import com.xiaozhi.service.SysKnowledgeDocService;
import com.xiaozhi.utils.CmsUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 知识库管理
 *
 * @author xiaozhi
 */
@RestController
@RequestMapping("/api/knowledge")
@Tag(name = "知识库管理", description = "知识库和文档的上传、查询、删除等操作")
public class KnowledgeController extends BaseController {

    @Resource
    private SysKnowledgeBaseService knowledgeBaseService;

    @Resource
    private SysKnowledgeDocService knowledgeDocService;

    @Autowired(required = false)
    private DocumentProcessingService documentProcessingService;

    @Value("${xiaozhi.rag.enabled:false}")
    private boolean ragEnabled;

    @Value("${xiaozhi.rag.upload-dir:./uploads/knowledge}")
    private String uploadDir;

    /**
     * 支持的文档类型
     */
    private static final Set<String> SUPPORTED_TYPES = Set.of("txt", "pdf", "md", "docx");

    // ==================== 知识库管理 ====================

    /**
     * 查询知识库列表
     */
    @GetMapping("/base")
    @ResponseBody
    @Operation(summary = "查询知识库列表", description = "分页查询当前用户的知识库")
    public ResultMessage listBases(SysKnowledgeBase base, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            base.setUserId(CmsUtils.getUserId());
            List<SysKnowledgeBase> list = knowledgeBaseService.listKnowledgeBases(base, pageFilter);

            ResultMessage result = ResultMessage.success();
            result.put("data", list);
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 创建知识库
     */
    @PostMapping("/base")
    @ResponseBody
    @Operation(summary = "创建知识库", description = "创建新的知识库")
    public ResultMessage createBase(@RequestBody SysKnowledgeBase base) {
        try {
            base.setUserId(CmsUtils.getUserId());
            base.setState("1");
            knowledgeBaseService.createKnowledgeBase(base);

            ResultMessage result = ResultMessage.success("创建成功");
            result.put("data", base);
            return result;
        } catch (Exception e) {
            logger.error("创建知识库失败: {}", e.getMessage(), e);
            return ResultMessage.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新知识库
     */
    @PutMapping("/base")
    @ResponseBody
    @Operation(summary = "更新知识库", description = "更新知识库名称和描述")
    public ResultMessage updateBase(@RequestBody SysKnowledgeBase base) {
        try {
            SysKnowledgeBase existing = knowledgeBaseService.selectById(base.getKnowledgeBaseId());
            if (existing == null) {
                return ResultMessage.error("知识库不存在");
            }
            if (!existing.getUserId().equals(CmsUtils.getUserId())) {
                return ResultMessage.error("无权操作该知识库");
            }
            knowledgeBaseService.updateKnowledgeBase(base);
            return ResultMessage.success("更新成功");
        } catch (Exception e) {
            logger.error("更新知识库失败: {}", e.getMessage(), e);
            return ResultMessage.error("更新失败");
        }
    }

    /**
     * 删除知识库
     */
    @DeleteMapping("/base/{knowledgeBaseId}")
    @ResponseBody
    @Operation(summary = "删除知识库", description = "逻辑删除知识库及其下所有文档")
    public ResultMessage deleteBase(@PathVariable Long knowledgeBaseId) {
        try {
            SysKnowledgeBase base = knowledgeBaseService.selectById(knowledgeBaseId);
            if (base == null) {
                return ResultMessage.error("知识库不存在");
            }
            if (!base.getUserId().equals(CmsUtils.getUserId())) {
                return ResultMessage.error("无权删除该知识库");
            }
            knowledgeBaseService.deleteKnowledgeBase(knowledgeBaseId);
            return ResultMessage.success("删除成功");
        } catch (Exception e) {
            logger.error("删除知识库失败: {}", e.getMessage(), e);
            return ResultMessage.error("删除失败");
        }
    }

    // ==================== 文档管理 ====================

    /**
     * 查询知识库文档列表
     */
    @GetMapping("/doc")
    @ResponseBody
    @Operation(summary = "查询知识库文档列表", description = "分页查询指定知识库下的文档")
    public ResultMessage listDocs(SysKnowledgeDoc doc, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            doc.setUserId(CmsUtils.getUserId());
            List<SysKnowledgeDoc> docList = knowledgeDocService.query(doc, pageFilter);

            ResultMessage result = ResultMessage.success();
            result.put("data", docList);
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 上传知识库文档
     */
    @PostMapping("/doc/upload")
    @ResponseBody
    @Operation(summary = "上传知识库文档", description = "上传文档到指定知识库并异步处理（文本提取、分块、向量化）")
    public ResultMessage upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeBaseId") Long knowledgeBaseId) {
        try {
            if (!ragEnabled || documentProcessingService == null) {
                return ResultMessage.error("RAG 知识库功能未启用，请在配置中设置 xiaozhi.rag.enabled=true");
            }

            // 验证知识库是否存在且属于当前用户
            SysKnowledgeBase base = knowledgeBaseService.selectById(knowledgeBaseId);
            if (base == null) {
                return ResultMessage.error("知识库不存在");
            }
            if (!base.getUserId().equals(CmsUtils.getUserId())) {
                return ResultMessage.error("无权操作该知识库");
            }

            if (file.isEmpty()) {
                return ResultMessage.error("文件不能为空");
            }

            // 检查文件类型
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null) {
                return ResultMessage.error("文件名不能为空");
            }
            String extension = getFileExtension(originalFilename).toLowerCase();
            if (!SUPPORTED_TYPES.contains(extension)) {
                return ResultMessage.error("不支持的文件类型: " + extension + "，支持的类型: " + SUPPORTED_TYPES);
            }

            // 保存文件到本地
            String savedPath = saveFile(file, extension);

            // 创建文档记录（W1：初始状态为 uploading）
            SysKnowledgeDoc doc = new SysKnowledgeDoc();
            doc.setUserId(CmsUtils.getUserId());
            doc.setKnowledgeBaseId(knowledgeBaseId);
            doc.setDocName(originalFilename);
            doc.setDocType(extension);
            doc.setFilePath(savedPath);
            doc.setFileSize(file.getSize());
            doc.setStatus("uploading");
            doc.setState("1");
            knowledgeDocService.add(doc);

            // 异步处理文档
            documentProcessingService.processDocumentAsync(doc);

            ResultMessage result = ResultMessage.success("文档上传成功，正在后台处理");
            result.put("data", doc);
            return result;

        } catch (Exception e) {
            logger.error("知识库文档上传失败: {}", e.getMessage(), e);
            return ResultMessage.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除知识库文档
     */
    @DeleteMapping("/doc/{docId}")
    @ResponseBody
    @Operation(summary = "删除知识库文档", description = "逻辑删除文档并清除对应的向量数据")
    public ResultMessage deleteDoc(@PathVariable Long docId) {
        try {
            SysKnowledgeDoc doc = knowledgeDocService.selectById(docId);
            if (doc == null) {
                return ResultMessage.error("文档不存在");
            }
            if (!doc.getUserId().equals(CmsUtils.getUserId())) {
                return ResultMessage.error("无权删除该文档");
            }

            // 逻辑删除文档记录
            knowledgeDocService.deleteById(docId);

            // 删除向量数据
            if (ragEnabled && documentProcessingService != null) {
                documentProcessingService.deleteDocumentVectors(docId);
            }

            return ResultMessage.success("删除成功");
        } catch (Exception e) {
            logger.error("删除知识库文档失败: {}", e.getMessage(), e);
            return ResultMessage.error("删除失败");
        }
    }

    /**
     * 重新处理文档
     */
    @PostMapping("/doc/{docId}/reprocess")
    @ResponseBody
    @Operation(summary = "重新处理文档", description = "对处理失败的文档重新执行文本提取和向量化")
    public ResultMessage reprocess(@PathVariable Long docId) {
        try {
            if (!ragEnabled || documentProcessingService == null) {
                return ResultMessage.error("RAG 知识库功能未启用");
            }

            SysKnowledgeDoc doc = knowledgeDocService.selectById(docId);
            if (doc == null) {
                return ResultMessage.error("文档不存在");
            }
            if (!doc.getUserId().equals(CmsUtils.getUserId())) {
                return ResultMessage.error("无权操作该文档");
            }

            // 异步重新处理
            documentProcessingService.processDocumentAsync(doc);

            return ResultMessage.success("已开始重新处理文档");
        } catch (Exception e) {
            logger.error("重新处理文档失败: {}", e.getMessage(), e);
            return ResultMessage.error("操作失败");
        }
    }

    /**
     * 查询文档状态（用于前端轮询）
     */
    @GetMapping("/doc/{docId}/status")
    @ResponseBody
    @Operation(summary = "查询文档处理状态", description = "查询指定文档的当前处理状态")
    public ResultMessage getDocStatus(@PathVariable Long docId) {
        try {
            SysKnowledgeDoc doc = knowledgeDocService.selectById(docId);
            if (doc == null) {
                return ResultMessage.error("文档不存在");
            }
            ResultMessage result = ResultMessage.success();
            result.put("data", doc);
            return result;
        } catch (Exception e) {
            logger.error("查询文档状态失败: {}", e.getMessage(), e);
            return ResultMessage.error("查询失败");
        }
    }

    /**
     * 保存上传的文件到本地
     */
    private String saveFile(MultipartFile file, String extension) throws IOException {
        Path dirPath = Paths.get(uploadDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        String fileName = UUID.randomUUID().toString() + "." + extension;
        Path filePath = dirPath.resolve(fileName);
        file.transferTo(filePath.toFile());
        return filePath.toString();
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }
}
