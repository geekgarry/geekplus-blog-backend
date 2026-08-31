package ${basePackage}.webapp.${moduleName}.entity;

import com.geekplus.common.domain.BaseEntity;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

<#list importList as import>
import ${import};
</#list>

/**
 * 功能：${functionName} 对象:${tableName}
 * <p>
 * 动态条件可查询字段在 Service {@code DynamicQueryHelper.prepare(entity, alias, fields...)} 声明。
 *
 * @author ${author}
 * @date ${date}
 */
public class ${modelNameUpperCamel} extends BaseEntity
{
    private static final long serialVersionUID = 1L;

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
