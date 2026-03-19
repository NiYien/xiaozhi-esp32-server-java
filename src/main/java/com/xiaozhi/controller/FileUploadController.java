package com.xiaozhi.controller;

import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.utils.FileUploadUtils;
import com.xiaozhi.utils.FileHashUtil;
import com.xiaozhi.utils.CmsUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件上传控制器
 * 
 */
@RestController
@RequestMapping("/api/file")
@Tag(name = "文件上传控制器", description = "文件上传相关操作")
public class FileUploadController {
    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @Value("${xiaozhi.upload-path:uploads}")
    private String uploadPath;

    @Autowired
    private CmsUtils cmsUtils;

    /**
     * 通用文件上传方法
     * 
     * @param file 上传的文件
     * @param type 文件类型（可选，用于分类存储）
     * @return 文件访问URL
     */
    @PostMapping("/upload")
    @ResponseBody
    @Operation(summary = "文件上传", description = "如果有配置腾讯云对象存储的话默认会存储到对象存储中")
    public ResultMessage uploadFile(
            @Parameter(description = "上传的文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "文件类型") @RequestParam(value = "type", required = false, defaultValue = "common") String type) {

        // 构建文件存储路径，按日期和类型分类
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String relativePath = type + "/" + datePath;

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        String fileName = UUID.randomUUID().toString().replaceAll("-", "") + extension;

        // 使用FileUploadUtils进行智能上传（根据配置自动选择上传到本地或腾讯云COS）
        try {
            String filePathOrUrl = FileUploadUtils.smartUpload(uploadPath, relativePath, fileName, file);
            logger.info("文件上传成功: {}", filePathOrUrl);

            // 计算文件哈希值
            String fileHash = FileHashUtil.calculateSha256(file);

            // 判断是否是COS URL（完整的https URL）
            boolean isCosUrl = filePathOrUrl.startsWith("https://") || filePathOrUrl.startsWith("http://");

            ResultMessage result = ResultMessage.success("上传成功");
            result.put("fileName", originalFilename);
            result.put("newFileName", fileName);
            result.put("hash", fileHash);

            // 如果是本地相对路径，转换为完整的访问URL
            if (!isCosUrl) {
                // filePathOrUrl 是相对路径，如：uploads/audio/2025/01/24/xxx.mp3
                String fullUrl = cmsUtils.getServerAddress() + "/" + filePathOrUrl;
                result.put("url", fullUrl);
                result.put("relativePath", filePathOrUrl); // 同时返回相对路径，方便存储
                logger.info("本地文件上传 - 相对路径: {}, 完整URL: {}", filePathOrUrl, fullUrl);
            } else {
                // COS URL 直接返回
                result.put("url", filePathOrUrl);
                logger.info("COS文件上传 - URL: {}", filePathOrUrl);
            }

            return result;
        } catch (Exception e) {
            logger.error("文件上传失败: {}", e.getMessage(), e);
            return ResultMessage.error("文件上传失败: " + e.getMessage());
        }
    }
}