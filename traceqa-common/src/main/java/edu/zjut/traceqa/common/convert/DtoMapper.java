package edu.zjut.traceqa.common.convert;

import edu.zjut.traceqa.common.model.po.ChatSession;
import edu.zjut.traceqa.common.model.po.KnowledgeBase;
import edu.zjut.traceqa.common.model.po.Role;
import edu.zjut.traceqa.common.model.po.SystemPrompt;
import edu.zjut.traceqa.common.model.po.User;
import edu.zjut.traceqa.common.model.vo.KnowledgeBaseDTO;
import edu.zjut.traceqa.common.model.vo.RoleDTO;
import edu.zjut.traceqa.common.model.vo.SessionVO;
import edu.zjut.traceqa.common.model.vo.SystemPromptDTO;
import edu.zjut.traceqa.common.model.vo.UserInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * MapStruct 对象映射器。
 *
 * <p>统一处理实体到 VO/DTO 的字段映射，替代手写 {@code of()} 方法。
 * {@code ChatMessageVO} 因涉及 JSON 到 List 的复杂转换，保留其手写 {@code of()}。</p>
 */
@Mapper
public interface DtoMapper {

    /**
     * 单例实例
     */
    DtoMapper INSTANCE = Mappers.getMapper(DtoMapper.class);

    /**
     * 会话实体转 VO
     */
    SessionVO toSessionVO(ChatSession session);

    /**
     * 用户实体 + 权限集合转用户信息
     */
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "permissions", source = "permissions")
    UserInfo toUserInfo(User user, List<String> permissions);

    /**
     * 知识库实体转 DTO
     */
    KnowledgeBaseDTO toKnowledgeBaseDTO(KnowledgeBase kb);

    /**
     * 系统提示词实体转 DTO
     */
    SystemPromptDTO toSystemPromptDTO(SystemPrompt prompt);

    /**
     * 角色实体转 DTO
     */
    RoleDTO toRoleDTO(Role role);
}