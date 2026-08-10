package com.geekplus.common.util;

import com.geekplus.common.domain.LoginBody;
import com.geekplus.framework.manager.AsyncManager;
import com.geekplus.framework.manager.LogFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * 登录日志：异步落库，避免同步查 IP / 写库拖慢登录接口。
 */
@Slf4j
public class LogUtil
{
    public static String getBlock(Object msg)
    {
        if (msg == null)
        {
            msg = "";
        }
        return "[" + msg.toString() + "]";
    }

    public static void recordLoginInfo(LoginBody loginBody, String status, String msg){
        String username = loginBody == null ? "" : loginBody.getUsername();
        AsyncManager.me().execute(LogFactory.recordLoginInfo(username, status, msg));
    }
}
