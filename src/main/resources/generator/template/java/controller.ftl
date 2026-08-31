package ${basePackage}.webapp.${moduleName}.controller;

import ${basePackage}.common.annotation.DataScope;
import ${basePackage}.common.annotation.Log;
import ${basePackage}.common.annotation.RepeatSubmit;
import ${basePackage}.common.core.controller.BaseController;
import ${basePackage}.common.domain.Result;
import ${basePackage}.common.enums.BusinessType;
import ${basePackage}.common.enums.OperatorType;
import ${basePackage}.common.util.poi.ExcelUtil;
import ${basePackage}.webapp.${moduleName}.entity.${modelNameUpperCamel};
import ${basePackage}.webapp.${moduleName}.service.${modelNameUpperCamel}Service;
import ${basePackage}.common.page.PageDataInfo;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.*;
import org.apache.shiro.authz.annotation.RequiresPermissions;

import javax.annotation.Resource;
import java.util.List;

/**
 * ${functionName}
 * Created by ${author} on ${date}.
 */
@RestController
@RequestMapping("${baseRequestMapping}")
public class ${modelNameUpperCamel}Controller extends BaseController {
    @Resource
    private ${modelNameUpperCamel}Service ${modelNameLowerCamel}Service;

    /**
     * 增加 ${functionName}
     */
    @RequiresPermissions("${permissionPrefix}:add")
    @Log(title = "新增${title}", businessType = BusinessType.INSERT, operatorType = OperatorType.MANAGE)
    @PostMapping("/add")
    @RepeatSubmit
    public Result add(@RequestBody ${modelNameUpperCamel} ${modelNameLowerCamel}) {
        return toResult(${modelNameLowerCamel}Service.add${modelNameUpperCamel}(${modelNameLowerCamel}));
    }

    /**
     * 增加 ${functionName}
     */
    @RequiresPermissions("${permissionPrefix}:add")
    @Log(title = "批量新增${title}", businessType = BusinessType.INSERT, operatorType = OperatorType.MANAGE)
    @PostMapping("/batchAdd")
    @RepeatSubmit
    public Result batchAdd(@RequestBody List<${modelNameUpperCamel}> ${modelNameLowerCamel}) {
    return toResult(${modelNameLowerCamel}Service.batchAdd${modelNameUpperCamel}List(${modelNameLowerCamel}));
    }

    /**
     * 删除 ${functionName}
     */
    @RequiresPermissions("${permissionPrefix}:delete")
    @Log(title = "删除${title}", businessType = BusinessType.DELETE, operatorType = OperatorType.MANAGE)
    @GetMapping("/delete")
    public Result remove(@RequestParam ${pkColumn.javaType} ${pkColumn.smallColumnName}) {
        return toResult(${modelNameLowerCamel}Service.remove${modelNameUpperCamel}ById(${pkColumn.smallColumnName}));
    }

    <#list allColumn as column>
    <#if column.columnName=='del_flag'>
    /**
    * 逻辑删除 ${functionName}
    */
    @RequiresPermissions("${permissionPrefix}:delete")
    @Log(title = "删除${title}", businessType = BusinessType.DELETE, operatorType = OperatorType.MANAGE)
    @GetMapping("/logical_delete")
    public Result modifyDelFlagById(@RequestParam ${pkColumn.javaType} ${pkColumn.smallColumnName}) {
        return toResult(${modelNameLowerCamel}Service.modifyDelFlagById(${pkColumn.smallColumnName}));
    }
    </#if>
    </#list>

    /**
     * 批量删除 ${functionName}
     */
    @RequiresPermissions("${permissionPrefix}:delete")
    @Log(title = "批量删除${title}", businessType = BusinessType.DELETE, operatorType = OperatorType.MANAGE)
    @DeleteMapping("/{${pkColumn.smallColumnName}s}")
    public Result remove(@PathVariable ${pkColumn.javaType}[] ${pkColumn.smallColumnName}s) {
        return toResult(${modelNameLowerCamel}Service.remove${modelNameUpperCamel}ByIds(${pkColumn.smallColumnName}s));
    }

    <#list allColumn as column>
    <#if column.columnName=='del_flag'>
    /**
    * 批量逻辑删除 ${functionName}
    */
    @RequiresPermissions("${permissionPrefix}:delete")
    @Log(title = "删除${title}", businessType = BusinessType.DELETE, operatorType = OperatorType.MANAGE)
    @DeleteMapping("/logical/{${pkColumn.smallColumnName}s}")
    public Result modifyDelFlagByIds(@PathVariable ${pkColumn.javaType}[] ${pkColumn.smallColumnName}s) {
        return toResult(${modelNameLowerCamel}Service.modifyDelFlagByIds(${pkColumn.smallColumnName}s));
    }
    </#if>
    </#list>

    /**
     * 更新 ${functionName}
     */
    @RequiresPermissions("${permissionPrefix}:update")
    @Log(title = "修改${title}", businessType = BusinessType.UPDATE, operatorType = OperatorType.MANAGE)
    @PostMapping("/update")
    public Result edit(@RequestBody ${modelNameUpperCamel} ${modelNameLowerCamel}) {
        return toResult(${modelNameLowerCamel}Service.modify${modelNameUpperCamel}(${modelNameLowerCamel}));
    }

    /**
     * 单条数据详情 ${functionName}
     */
    @RequiresPermissions("${permissionPrefix}:info")
    @GetMapping("/{${pkColumn.smallColumnName}}")
    public Result info(@PathVariable ${pkColumn.javaType} ${pkColumn.smallColumnName}) {
        ${modelNameUpperCamel} ${modelNameLowerCamel} = ${modelNameLowerCamel}Service.query${modelNameUpperCamel}ById(${pkColumn.smallColumnName});
        return Result.success(${modelNameLowerCamel});
    }

    /**
     * 条件查询所有 ${functionName}
     */
    @GetMapping("/listAll")
    public PageDataInfo listAll(${modelNameUpperCamel} ${modelNameLowerCamel}) {
        List<${modelNameUpperCamel}> list = ${modelNameLowerCamel}Service.query${modelNameUpperCamel}List(${modelNameLowerCamel});
        PageDataInfo rspData = new PageDataInfo();
        rspData.setCode(200);
        rspData.setMsg("查询成功");
        rspData.setRows(list);
        rspData.setTotal(new PageInfo(list).getTotal());
        return rspData;
    }

    /**
     * 列表 GET（默认）：conditionsJson / 扁平字段走 query；Header X-GP-Conditions-Json 由 prepare 兜底。
     */
    @RequiresPermissions("${permissionPrefix}:list")
    @DataScope(deptAlias = "${tableAlias}", userAlias = "${tableAlias}")
    @GetMapping("/list")
    public PageDataInfo list(${modelNameUpperCamel} ${modelNameLowerCamel},
                             @RequestParam(value = "conditionsJson", required = false) String conditionsJson) {
        return doList(mergeConditionsJson(${modelNameLowerCamel}, conditionsJson));
    }

    /**
     * 列表 POST：与 GET 同逻辑，筛选项从 RequestBody 取。
     */
    @RequiresPermissions("${permissionPrefix}:list")
    @DataScope(deptAlias = "${tableAlias}", userAlias = "${tableAlias}")
    @PostMapping("/list")
    public PageDataInfo listPost(@RequestBody(required = false) ${modelNameUpperCamel} body,
                                 @RequestParam(value = "conditionsJson", required = false) String conditionsJson) {
        return doList(mergeConditionsJson(body, conditionsJson));
    }

    private PageDataInfo doList(${modelNameUpperCamel} ${modelNameLowerCamel}) {
        startPage();
        List<${modelNameUpperCamel}> list = ${modelNameLowerCamel}Service.query${modelNameUpperCamel}List(${modelNameLowerCamel});
        return getDataTable(list);
    }

    private static ${modelNameUpperCamel} mergeConditionsJson(${modelNameUpperCamel} entity, String conditionsJson) {
        if (entity == null) {
            entity = new ${modelNameUpperCamel}();
        }
        if ((entity.getConditionsJson() == null || entity.getConditionsJson().length() == 0)
                && conditionsJson != null && conditionsJson.length() > 0) {
            entity.setConditionsJson(conditionsJson);
        }
        return entity;
    }

    /**
    * 导出数据字典类型
    */
    @RequiresPermissions("${permissionPrefix}:export")
    @Log(title = "导出${title}", businessType = BusinessType.EXPORT, operatorType = OperatorType.MANAGE)
    @GetMapping("/export")
    public Result export(${modelNameUpperCamel} ${modelNameLowerCamel}){
        List<${modelNameUpperCamel}> list = ${modelNameLowerCamel}Service.query${modelNameUpperCamel}List(${modelNameLowerCamel});
        ExcelUtil<${modelNameUpperCamel}> util = new ExcelUtil<${modelNameUpperCamel}>(${modelNameUpperCamel}.class);
        return util.exportExcel(list, "${modelNameLowerCamel}");
    }
}
