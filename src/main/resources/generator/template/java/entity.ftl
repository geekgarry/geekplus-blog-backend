package ${basePackage}.webapp.${moduleName}.entity;

import com.geekplus.common.domain.BaseEntity;
import com.geekplus.common.query.DynamicQueryColumns;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

<#list importList as import>
import ${import};
</#list>
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 功能：${functionName} 对象:${tableName}
 *
 * @author ${author}
 * @date ${date}
 */
public class ${modelNameUpperCamel} extends BaseEntity implements DynamicQueryColumns
{
    private static final long serialVersionUID = 1L;

    /** 动态查询白名单：前端 field → 列名（排除主键/密码/审计敏感列可按需调整） */
    private static final Map<String, String> DYNAMIC_QUERY_COLUMNS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
<#if allColumn?exists>
<#list allColumn as column>
<#if column.isPk!='1' && column.smallColumnName != 'password' && column.smallColumnName != 'delFlag'>
        m.put("${column.smallColumnName}", "${column.columnName}");
</#if>
</#list>
</#if>
        DYNAMIC_QUERY_COLUMNS = Collections.unmodifiableMap(m);
    }

    @Override
    public Map<String, String> dynamicQueryColumns() {
        return DYNAMIC_QUERY_COLUMNS;
    }

    <#list allColumn as column>

    /**
     * ${functionName} ${title}
     */
    private ${column.javaType} ${column.smallColumnName};
    </#list>

    <#list allColumn as column>
	/**
	 *获取${column.columnComment}
	 */
	public ${column.javaType} get${column.bigColumnName}(){
		return ${column.smallColumnName};
	}

	/**
	 *设置${column.columnComment}
	 */
	public void set${column.bigColumnName}(${column.javaType} ${column.smallColumnName}){
		this.${column.smallColumnName} = ${column.smallColumnName};
	}
	</#list>

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
    <#list allColumn as column>
            .append("${column.smallColumnName}", get${column.bigColumnName}())
    </#list>
            .toString();
    }
}
