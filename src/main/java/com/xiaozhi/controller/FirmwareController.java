package com.xiaozhi.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.entity.SysFirmware;
import com.xiaozhi.service.SysFirmwareService;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.DtoConverter;
import com.xiaozhi.utils.FileHashUtil;
import com.xiaozhi.utils.FileUploadUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 固件管理
 *
 */
@RestController
@RequestMapping("/api/firmware")
@Tag(name = "固件管理", description = "固件上传、查询、删除、设置默认版本等操作")
public class FirmwareController extends BaseController {

    @Resource
    private SysFirmwareService firmwareService;

    @Resource
    private CmsUtils cmsUtils;

    @Value("${xiaozhi.upload-path:uploads}")
    private String uploadPath;

    /**
     * 上传固件
     */
    @PostMapping("/upload")
    @ResponseBody
    @Operation(summary = "上传固件", description = "上传固件文件及元数据")
    public ResultMessage upload(
            @Parameter(description = "固件文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "固件名称") @RequestParam("firmwareName") String firmwareName,
            @Parameter(description = "版本号") @RequestParam("version") String version,
            @Parameter(description = "适用芯片型号") @RequestParam(value = "chipModelName", required = false) String chipModelName,
            @Parameter(description = "适用设备类型") @RequestParam(value = "deviceType", required = false) String deviceType,
            @Parameter(description = "版本说明") @RequestParam(value = "description", required = false) String description,
            @Parameter(description = "是否设为默认") @RequestParam(value = "isDefault", required = false, defaultValue = "0") String isDefault) {
        try {
            // 构建文件存储路径
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
            String relativePath = "firmware/" + datePath;

            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".bin";
            String fileName = UUID.randomUUID().toString().replaceAll("-", "") + extension;

            // 上传文件
            String filePathOrUrl = FileUploadUtils.smartUpload(uploadPath, relativePath, fileName, file);

            // 计算文件哈希
            String fileHash = FileHashUtil.calculateSha256(file);

            // 判断是否为COS URL
            boolean isCosUrl = filePathOrUrl.startsWith("https://") || filePathOrUrl.startsWith("http://");
            String firmwareUrl = isCosUrl ? filePathOrUrl : cmsUtils.getServerAddress() + "/" + filePathOrUrl;

            // 创建固件记录
            SysFirmware firmware = new SysFirmware();
            firmware.setFirmwareName(firmwareName);
            firmware.setVersion(version);
            firmware.setChipModelName(chipModelName);
            firmware.setDeviceType(deviceType);
            firmware.setUrl(firmwareUrl);
            firmware.setFileSize(file.getSize());
            firmware.setFileHash(fileHash);
            firmware.setDescription(description);
            firmware.setIsDefault(isDefault);
            firmware.setUserId(CmsUtils.getUserId());

            firmwareService.add(firmware);

            return ResultMessage.success(DtoConverter.toFirmwareDTO(firmware));
        } catch (Exception e) {
            logger.error("上传固件失败", e);
            return ResultMessage.error("上传固件失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询固件列表
     */
    @GetMapping("")
    @ResponseBody
    @Operation(summary = "查询固件列表", description = "分页查询固件信息列表")
    public ResultMessage list(SysFirmware firmware, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            List<SysFirmware> firmwareList = firmwareService.query(firmware, pageFilter);

            ResultMessage result = ResultMessage.success();
            result.put("data", DtoConverter.toPageInfo(firmwareList, DtoConverter::toFirmwareDTOList));
            return result;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 更新固件信息
     */
    @PutMapping("/{firmwareId}")
    @ResponseBody
    @Operation(summary = "更新固件信息", description = "更新固件名称、版本说明等信息")
    public ResultMessage update(@PathVariable Integer firmwareId,
                                @Parameter(description = "固件名称") @RequestParam(value = "firmwareName", required = false) String firmwareName,
                                @Parameter(description = "版本号") @RequestParam(value = "version", required = false) String version,
                                @Parameter(description = "版本说明") @RequestParam(value = "description", required = false) String description,
                                @Parameter(description = "适用芯片型号") @RequestParam(value = "chipModelName", required = false) String chipModelName,
                                @Parameter(description = "适用设备类型") @RequestParam(value = "deviceType", required = false) String deviceType) {
        try {
            SysFirmware firmware = new SysFirmware();
            firmware.setFirmwareId(firmwareId);
            firmware.setFirmwareName(firmwareName);
            firmware.setVersion(version);
            firmware.setDescription(description);
            firmware.setChipModelName(chipModelName);
            firmware.setDeviceType(deviceType);

            int rows = firmwareService.update(firmware);
            if (rows > 0) {
                SysFirmware updatedFirmware = firmwareService.selectById(firmwareId);
                return ResultMessage.success(DtoConverter.toFirmwareDTO(updatedFirmware));
            }
            return ResultMessage.error("更新失败");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 删除固件
     */
    @DeleteMapping("/{firmwareId}")
    @ResponseBody
    @Operation(summary = "删除固件", description = "删除固件记录")
    public ResultMessage delete(@PathVariable Integer firmwareId) {
        try {
            int rows = firmwareService.delete(firmwareId);
            if (rows > 0) {
                return ResultMessage.success("删除成功");
            }
            return ResultMessage.error("删除失败");
        } catch (Exception e) {
            logger.error("删除固件失败", e);
            return ResultMessage.error("删除固件失败");
        }
    }

    /**
     * 设置固件为默认
     */
    @PutMapping("/{firmwareId}/default")
    @ResponseBody
    @Operation(summary = "设置默认固件", description = "将指定固件设置为其芯片型号和设备类型组合下的默认固件")
    public ResultMessage setDefault(@PathVariable Integer firmwareId) {
        try {
            int rows = firmwareService.setDefault(firmwareId);
            if (rows > 0) {
                SysFirmware updatedFirmware = firmwareService.selectById(firmwareId);
                return ResultMessage.success(DtoConverter.toFirmwareDTO(updatedFirmware));
            }
            return ResultMessage.error("设置默认固件失败");
        } catch (Exception e) {
            logger.error("设置默认固件失败", e);
            return ResultMessage.error("设置默认固件失败");
        }
    }
}
