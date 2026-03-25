package com.xiaozhi.dao;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.xiaozhi.entity.SysMessage;
import org.apache.ibatis.annotations.Param;

/**
 * 聊天记录 数据层
 * 
 * 
 */
public interface MessageMapper {

  int add(SysMessage message);

  int saveAll(List<SysMessage> messages);

  int delete(SysMessage message);

  List<SysMessage> query(SysMessage message);

  List<SysMessage> find(String deviceId, int roleId, int  limit);

  List<SysMessage> findAfter(String deviceId, int roleId, Instant timeMillis);

    /**
     * 更新消息的音频数据信息
     *
     * @param sysMessage
     */
  void updateMessageByAudioFile(SysMessage sysMessage);

  /**
   * 根据消息ID查询消息
   */
  SysMessage findById(Integer messageId);

  /**
   * 按 sessionId 分组聚合查询对话列表
   */
  List<Map<String, Object>> querySessions(@Param("userId") Integer userId,
                                           @Param("deviceId") String deviceId,
                                           @Param("startTime") Date startTime,
                                           @Param("endTime") Date endTime);

  /**
   * 更新指定 sessionId 的首条消息的 session_title
   */
  int updateSessionTitle(@Param("sessionId") String sessionId, @Param("sessionTitle") String sessionTitle);

  /**
   * 查询所有无标题且消息数 >= 2 的 sessionId 列表
   */
  List<String> querySessionsWithoutTitle(@Param("userId") Integer userId);

  /**
   * 查询指定 sessionId 的前 N 条消息（用于批量生成标题）
   */
  List<SysMessage> queryMessagesBySessionId(@Param("sessionId") String sessionId, @Param("limit") int limit);

  /**
   * 查询指定设备的用户音频消息列表
   */
  List<SysMessage> queryUserAudios(@Param("userId") Integer userId, @Param("deviceId") String deviceId);

  /**
   * 查询同设备同会话最近一条 user 消息（用于 audioGroup 计算）
   */
  SysMessage findLastUserMessage(@Param("deviceId") String deviceId, @Param("sessionId") String sessionId);

  /**
   * 查询过期的用户音频消息（用于清理任务）
   */
  List<SysMessage> queryExpiredUserAudios(@Param("expireTime") Date expireTime);

  /**
   * 批量清空音频路径
   */
  int clearAudioPaths(@Param("messageIds") List<Integer> messageIds);

  /**
   * 根据消息ID列表批量查询
   */
  List<SysMessage> findByIds(@Param("messageIds") List<Integer> messageIds);
}