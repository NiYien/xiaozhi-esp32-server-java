package com.xiaozhi.controller;

import com.github.pagehelper.PageInfo;
import com.xiaozhi.common.web.ResultMessage;
import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.dto.response.MessageDTO;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.utils.AudioUtils;
import com.xiaozhi.utils.CmsUtils;
import com.xiaozhi.utils.DtoConverter;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;


/**
 * @Date: 2025/2/28 下午2:46
 * @Description:
 */

@RestController
@RequestMapping("/api/message")
@Tag(name = "消息管理", description = "消息相关操作")
public class MessageController extends BaseController {

    @Resource
    private SysMessageService sysMessageService;

    // 后续考虑：未来如果将设备对话与管理台分离部署，则此SessionManager将不可使用。
    @Resource
    private SessionManager sessionManager;
    /**
     * 按 sessionId 分组查询对话列表
     */
    @GetMapping("/sessions")
    @ResponseBody
    @Operation(summary = "查询对话列表", description = "按 sessionId 分组聚合查询对话列表")
    public ResultMessage sessions(
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            Integer userId = CmsUtils.getUserId();
            List<Map<String, Object>> sessions = sysMessageService.querySessions(userId, deviceId, startTime, endTime, pageFilter);
            return ResultMessage.success(new PageInfo<>(sessions));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 查询对话
     *
     * @param message
     * @return
     */
    @GetMapping("")
    @ResponseBody
    @Operation(summary = "根据条件查询对话消息", description = "返回对话消息列表")
    public ResultMessage list(SysMessage message, HttpServletRequest request) {
        try {
            PageFilter pageFilter = initPageFilter(request);
            message.setUserId(CmsUtils.getUserId());
            List<SysMessage> messageList = sysMessageService.query(message, pageFilter);

            return ResultMessage.success(DtoConverter.toPageInfo(messageList, DtoConverter::toMessageDTOList));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 删除聊天记录
     *
     * @param messageId 消息ID
     * @return
     */
    @DeleteMapping("/{messageId}")
    @ResponseBody
    @Operation(summary = "删除对话消息", description = "删除指定的对话消息，逻辑删除")
    public ResultMessage delete(@PathVariable Integer messageId) {
        try {
            SysMessage message = new SysMessage();
            message.setMessageId(messageId);
            message.setUserId(CmsUtils.getUserId());

            int rows = sysMessageService.delete(message);
            logger.info("删除聊天记录：{}行。", rows);
            return ResultMessage.success("删除成功，共删除" + rows + "条消息");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return ResultMessage.error();
        }
    }

    /**
     * 批量为历史无标题的会话生成 LLM 标题
     */
    @PostMapping("/generateTitles")
    @ResponseBody
    @Operation(summary = "批量生成对话标题", description = "为所有无标题的历史会话调用 LLM 生成标题")
    public ResultMessage generateTitles() {
        try {
            Integer userId = CmsUtils.getUserId();
            Map<String, Integer> result = sysMessageService.batchGenerateTitles(userId);
            return ResultMessage.success("批量生成完成", result);
        } catch (Exception e) {
            logger.error("批量生成对话标题失败", e);
            return ResultMessage.error("批量生成失败: " + e.getMessage());
        }
    }

    /**
     * 查询指定设备的用户音频消息列表
     */
    @GetMapping("/userAudios")
    @ResponseBody
    @Operation(summary = "查询用户音频列表", description = "返回指定设备的有音频的用户消息列表，含 audioGroup 和 audioDuration")
    public ResultMessage userAudios(@RequestParam(required = false) String deviceId) {
        try {
            Integer userId = CmsUtils.getUserId();
            List<SysMessage> messages = sysMessageService.queryUserAudios(userId, deviceId);

            // 转为 DTO 并计算音频时长
            List<MessageDTO> dtoList = messages.stream().map(msg -> {
                MessageDTO dto = DtoConverter.toMessageDTO(msg);
                if (msg.getAudioPath() != null && !msg.getAudioPath().isBlank()) {
                    try {
                        double duration = AudioUtils.getAudioDuration(Path.of(msg.getAudioPath()));
                        dto.setAudioDuration(duration > 0 ? duration : null);
                    } catch (Exception e) {
                        // 音频文件不存在时忽略
                    }
                }
                return dto;
            }).toList();

            ResultMessage result = ResultMessage.success();
            result.put("data", dtoList);
            return result;
        } catch (Exception e) {
            logger.error("查询用户音频列表失败", e);
            return ResultMessage.error();
        }
    }

    /**
     * 批量删除设备聊天记录（清除设备记忆）
     *
     * @param deviceId 设备ID
     * @return
     */
    @DeleteMapping("")
    @ResponseBody
    @Operation(summary = "批量删除设备消息", description = "清除指定设备的所有聊天记录")
    public ResultMessage batchDelete(@RequestParam(required = true) String deviceId) {
        try {
            SysMessage message = new SysMessage();
            message.setDeviceId(deviceId);
            message.setUserId(CmsUtils.getUserId());

            int rows = sysMessageService.delete(message);
            logger.info("清除设备记忆，删除聊天记录：{}行。", rows);
            return ResultMessage.success("删除成功，共删除" + rows + "条消息");
        } catch (Exception e) {
            logger.error("批量删除消息失败", e);
            return ResultMessage.error("删除失败");
        }
    }

}