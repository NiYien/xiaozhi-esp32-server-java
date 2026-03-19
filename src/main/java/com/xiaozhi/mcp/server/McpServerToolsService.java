package com.xiaozhi.mcp.server;

import com.xiaozhi.communication.common.ChatSession;
import com.xiaozhi.communication.common.SessionManager;
import com.xiaozhi.communication.domain.iot.IotDescriptor;
import com.xiaozhi.communication.domain.iot.IotProperty;
import com.xiaozhi.dialogue.service.IotService;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.entity.SysMessage;
import com.xiaozhi.service.SysDeviceService;
import com.xiaozhi.service.SysMessageService;
import com.xiaozhi.common.web.PageFilter;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * MCP Server 工具实现服务
 * 提供设备列表查询、设备状态查询、IoT控制、消息发送、对话历史查询等工具
 */
@Service
public class McpServerToolsService {

    private static final Logger logger = LoggerFactory.getLogger(McpServerToolsService.class);
    private static final String TAG = "McpServerTools";

    @Resource
    private SysDeviceService deviceService;

    @Resource
    private SysMessageService messageService;

    @Resource
    private SessionManager sessionManager;

    @Resource
    private IotService iotService;

    /**
     * 获取用户的设备列表
     *
     * @param userId 用户ID
     * @return 设备列表信息
     */
    public List<Map<String, Object>> getDeviceList(Integer userId) {
        SysDevice query = new SysDevice();
        query.setUserId(userId);
        List<SysDevice> devices = deviceService.query(query, null);

        List<Map<String, Object>> result = new ArrayList<>();
        for (SysDevice device : devices) {
            Map<String, Object> deviceInfo = new LinkedHashMap<>();
            deviceInfo.put("deviceId", device.getDeviceId());
            deviceInfo.put("deviceName", device.getDeviceName());
            // 判断设备是否在线（有活跃的WebSocket会话）
            ChatSession session = sessionManager.getSessionByDeviceId(device.getDeviceId());
            String state = (session != null && session.isOpen()) ? "online" : "offline";
            deviceInfo.put("state", state);
            deviceInfo.put("roleName", device.getRoleName());
            result.add(deviceInfo);
        }
        return result;
    }

    /**
     * 获取设备状态和IoT属性
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @return 设备状态信息
     */
    public Map<String, Object> getDeviceStatus(Integer userId, String deviceId) {
        // 权限校验
        SysDevice device = deviceService.selectDeviceById(deviceId);
        if (device == null || !userId.equals(device.getUserId())) {
            return Map.of("error", "设备不存在或无权访问");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceId", device.getDeviceId());
        result.put("deviceName", device.getDeviceName());
        result.put("ip", device.getIp());
        result.put("version", device.getVersion());
        result.put("roleName", device.getRoleName());

        ChatSession session = sessionManager.getSessionByDeviceId(deviceId);
        if (session != null && session.isOpen()) {
            result.put("state", "online");
            // 获取IoT属性描述列表
            Map<String, IotDescriptor> descriptors = session.getIotDescriptors();
            List<Map<String, Object>> iotProperties = new ArrayList<>();
            for (Map.Entry<String, IotDescriptor> entry : descriptors.entrySet()) {
                IotDescriptor descriptor = entry.getValue();
                Map<String, Object> iotInfo = new LinkedHashMap<>();
                iotInfo.put("name", descriptor.getName());
                iotInfo.put("description", descriptor.getDescription());

                List<Map<String, Object>> props = new ArrayList<>();
                for (Map.Entry<String, IotProperty> propEntry : descriptor.getProperties().entrySet()) {
                    IotProperty prop = propEntry.getValue();
                    Map<String, Object> propInfo = new LinkedHashMap<>();
                    propInfo.put("name", propEntry.getKey());
                    propInfo.put("description", prop.getDescription());
                    propInfo.put("type", prop.getType());
                    propInfo.put("value", prop.getValue());
                    props.add(propInfo);
                }
                iotInfo.put("properties", props);
                iotProperties.add(iotInfo);
            }
            result.put("iotDevices", iotProperties);
        } else {
            result.put("state", "offline");
            result.put("iotDevices", Collections.emptyList());
        }
        return result;
    }

    /**
     * 控制IoT设备属性
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @param property 属性名（格式: iotName.propertyName）
     * @param value    属性值
     * @return 操作结果
     */
    public String controlDevice(Integer userId, String deviceId, String property, Object value) {
        // 权限校验
        SysDevice device = deviceService.selectDeviceById(deviceId);
        if (device == null || !userId.equals(device.getUserId())) {
            return "设备不存在或无权访问";
        }

        ChatSession session = sessionManager.getSessionByDeviceId(deviceId);
        if (session == null || !session.isOpen()) {
            return "设备离线，无法执行控制操作";
        }

        // 解析 property 格式: iotName.propertyName
        String[] parts = property.split("\\.", 2);
        if (parts.length != 2) {
            return "属性格式不正确，请使用 iotName.propertyName 格式";
        }

        String iotName = parts[0];
        String propName = parts[1];

        // 检查IoT设备和属性是否存在
        IotDescriptor descriptor = session.getIotDescriptors().get(iotName);
        if (descriptor == null) {
            return "不支持的IoT设备: " + iotName;
        }

        IotProperty iotProperty = descriptor.getProperties().get(propName);
        if (iotProperty == null) {
            return "不支持的属性: " + property;
        }

        boolean success = iotService.setIotStatus(session.getSessionId(), iotName, propName, value);
        return success ? "操作成功" : "操作失败";
    }

    /**
     * 向设备发送文本消息（触发TTS播放）
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @param message  消息内容
     * @return 操作结果
     */
    public String sendMessage(Integer userId, String deviceId, String message) {
        // 权限校验
        SysDevice device = deviceService.selectDeviceById(deviceId);
        if (device == null || !userId.equals(device.getUserId())) {
            return "设备不存在或无权访问";
        }

        ChatSession session = sessionManager.getSessionByDeviceId(deviceId);
        if (session == null || !session.isOpen()) {
            return "设备离线，无法发送消息";
        }

        try {
            // 通过 Persona 的 Synthesizer 触发完整的 TTS 合成和播放管道
            com.xiaozhi.dialogue.service.Persona persona = session.getPersona();
            if (persona == null) {
                return "设备未初始化对话";
            }
            com.xiaozhi.dialogue.service.Synthesizer synthesizer = persona.getSynthesizer();
            if (synthesizer == null) {
                return "设备未初始化对话";
            }
            synthesizer.synthesize(message);
            return "消息已发送";
        } catch (Exception e) {
            logger.error("[{}] 向设备发送消息失败 - DeviceId: {}, Error: {}", TAG, deviceId, e.getMessage());
            return "消息发送失败: " + e.getMessage();
        }
    }

    /**
     * 获取设备对话历史
     *
     * @param userId   用户ID
     * @param deviceId 设备ID
     * @param limit    返回记录数量
     * @return 对话历史列表
     */
    public List<Map<String, Object>> getChatHistory(Integer userId, String deviceId, int limit) {
        // 权限校验
        SysDevice device = deviceService.selectDeviceById(deviceId);
        if (device == null || !userId.equals(device.getUserId())) {
            return List.of(Map.of("error", "设备不存在或无权访问"));
        }

        SysMessage queryMsg = new SysMessage();
        queryMsg.setDeviceId(deviceId);
        queryMsg.setUserId(userId);

        PageFilter pageFilter = new PageFilter();
        pageFilter.setStart(0);
        pageFilter.setLimit(Math.min(limit, 100));

        List<SysMessage> messages = messageService.query(queryMsg, pageFilter);

        List<Map<String, Object>> result = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (SysMessage msg : messages) {
            Map<String, Object> msgInfo = new LinkedHashMap<>();
            msgInfo.put("sender", msg.getSender());
            msgInfo.put("message", msg.getMessage());
            msgInfo.put("createTime", msg.getCreateTime() != null ? sdf.format(msg.getCreateTime()) : null);
            result.add(msgInfo);
        }
        return result;
    }

    /**
     * 获取所有工具定义（用于 tools/list 响应）
     *
     * @return 工具定义列表
     */
    public List<Map<String, Object>> getToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // get_device_list
        tools.add(buildToolDef("get_device_list", "获取用户的设备列表，返回设备ID、名称、在线状态、角色名称",
                Map.of("type", "object", "properties", Map.of(), "required", List.of())));

        // get_device_status
        tools.add(buildToolDef("get_device_status", "获取指定设备的详细状态和IoT属性信息",
                Map.of("type", "object",
                        "properties", Map.of(
                                "deviceId", Map.of("type", "string", "description", "设备ID")),
                        "required", List.of("deviceId"))));

        // control_device
        tools.add(buildToolDef("control_device", "控制IoT设备属性，如开关灯、调节温度等",
                Map.of("type", "object",
                        "properties", Map.of(
                                "deviceId", Map.of("type", "string", "description", "设备ID"),
                                "property", Map.of("type", "string", "description", "属性名，格式为 iotName.propertyName"),
                                "value", Map.of("type", "string", "description", "属性值，类型取决于属性定义")),
                        "required", List.of("deviceId", "property", "value"))));

        // send_message
        tools.add(buildToolDef("send_message", "向设备发送文本消息，设备会通过TTS语音播放",
                Map.of("type", "object",
                        "properties", Map.of(
                                "deviceId", Map.of("type", "string", "description", "设备ID"),
                                "message", Map.of("type", "string", "description", "要发送的文本消息")),
                        "required", List.of("deviceId", "message"))));

        // get_chat_history
        tools.add(buildToolDef("get_chat_history", "获取设备的对话历史记录",
                Map.of("type", "object",
                        "properties", Map.of(
                                "deviceId", Map.of("type", "string", "description", "设备ID"),
                                "limit", Map.of("type", "integer", "description", "返回记录数量，默认20，最大100")),
                        "required", List.of("deviceId"))));

        return tools;
    }

    private Map<String, Object> buildToolDef(String name, String description, Map<String, Object> inputSchema) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", name);
        tool.put("description", description);
        tool.put("inputSchema", inputSchema);
        return tool;
    }
}
