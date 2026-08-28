package edu.zjut.traceqa.common.convert;

import edu.zjut.traceqa.model.vo.RoleDTO;
import edu.zjut.traceqa.model.vo.UserInfo;
import edu.zjut.traceqa.model.vo.SessionVO;
import edu.zjut.traceqa.model.vo.KnowledgeBaseDTO;
import edu.zjut.traceqa.model.vo.SystemPromptDTO;
import edu.zjut.traceqa.model.po.ChatSession;
import edu.zjut.traceqa.model.po.KnowledgeBase;
import edu.zjut.traceqa.model.po.Role;
import edu.zjut.traceqa.model.po.SystemPrompt;
import edu.zjut.traceqa.model.po.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 统一 DTO 映射（MapStruct），替代各 DTO 手写 {@code of()} 转换方法。
 * 注：ChatMessageVO 涉及 JSON→List 的复杂转换，保留其手写 of()。
 */
@Mapper
public interface DtoMapper {

    DtoMapper INSTANCE = Mappers.getMapper(DtoMapper.class);

    SessionVO toSessionVO(ChatSession session);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "permissions", source = "permissions")
    UserInfo toUserInfo(User user, List<String> permissions);

    KnowledgeBaseDTO toKnowledgeBaseDTO(KnowledgeBase kb);

    SystemPromptDTO toSystemPromptDTO(SystemPrompt prompt);

    RoleDTO toRoleDTO(Role role);
}
