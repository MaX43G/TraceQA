package edu.zjut.traceqa.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统提示词实体。
 *
 * <p>支持管理员动态管理各类 Agent 的系统提示词（会话场景、查询重写、HyDE 生成、
 * 总结回答等），修改后立即生效，无需重启。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_system_prompt")
public class SystemPrompt extends BaseEntity {

    /** 提示词场景编码（chat/rewrite/hyde/summary...），同一场景仅一个生效 */
    private String scenario;

    /** 提示词名称 */
    private String name;

    /** 提示词内容（支持 {placeholder} 模板变量） */
    private String content;

    /** 是否启用：1 启用，0 停用 */
    private Integer enabled;

    /** 备注 */
    private String remark;
}