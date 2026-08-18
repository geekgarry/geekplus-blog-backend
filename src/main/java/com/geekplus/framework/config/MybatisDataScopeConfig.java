package com.geekplus.framework.config;

import com.geekplus.framework.interceptor.mybatis.DataScopeInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * 显式把数据权限插件挂到 MyBatis（系统主路径是 AOP+XML；插件作兜底，
 * 且须同时拦截 4/6 参 query，否则 PageHelper 走 6 参时插件不生效）。
 */
@Configuration
public class MybatisDataScopeConfig
{
    @Autowired
    private List<SqlSessionFactory> sqlSessionFactoryList;

    @Autowired
    private DataScopeInterceptor dataScopeInterceptor;

    @PostConstruct
    public void addInterceptor()
    {
        if (sqlSessionFactoryList == null || dataScopeInterceptor == null)
        {
            return;
        }
        for (SqlSessionFactory factory : sqlSessionFactoryList)
        {
            org.apache.ibatis.session.Configuration conf = factory.getConfiguration();
            // 避免重复注册（热部署 / 多 factory）
            if (!conf.getInterceptors().contains(dataScopeInterceptor))
            {
                conf.addInterceptor(dataScopeInterceptor);
            }
        }
    }
}
