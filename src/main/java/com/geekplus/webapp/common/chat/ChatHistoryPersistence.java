package com.geekplus.webapp.common.chat;

import com.geekplus.webapp.function.entity.ChatAILog;
import com.geekplus.webapp.function.service.IChatAILogService;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 对话持久化：登录用户与 guest 均写 DB（guest 便于后台按 IP/用户名审计）。
 */
@Slf4j
public final class ChatHistoryPersistence {

    private ChatHistoryPersistence() {
    }

    /**
     * 写入 chat_ai_log（guest / 登录用户均 insert）。
     *
     * @return 是否已写入 DB
     */
    public static boolean saveChatLogIfMember(IChatAILogService chatLogService, ChatAILog chatAILog, String username) {
        if (chatLogService == null || chatAILog == null) {
            return false;
        }
        chatLogService.insertChatAILog(chatAILog);
        return true;
    }
}
