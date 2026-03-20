package com.xiaozhi.service;

import com.xiaozhi.common.web.PageFilter;
import com.xiaozhi.entity.SysMessage;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 聊天记录查询/添加
 * 
 * 
 */
public interface SysMessageService {

  /**
   * 新增记录
   * 
   * @param message
   * @return
   */
  int add(SysMessage message);

  @Transactional
  int saveAll(List<SysMessage> messages);

  /**
   * 查询聊天记录
   * 指定分页信息
   * @param message
   * @return
   */
  List<SysMessage> query(SysMessage message, PageFilter pageFilter);

  /**
   * 根据消息ID查询消息
   */
  SysMessage findById(Integer messageId);

  /**
   * 删除记忆
   * 
   * @param message
   * @return
   */
  int delete(SysMessage message);

  /**
   * 按 sessionId 分组聚合查询对话列表
   */
  List<Map<String, Object>> querySessions(Integer userId, String deviceId, Date startTime, Date endTime, PageFilter pageFilter);

  /**
   * 更新消息的音频数据信息
   */
  void updateMessageByAudioFile(String deviceId, Integer roleId, String sender, String createTime,
                                String audioPath);

  /**
   * 批量为历史无标题的会话生成 LLM 标题
   * @return {total, success, failed}
   */
  Map<String, Integer> batchGenerateTitles(Integer userId);
}