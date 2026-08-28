package edu.zjut.traceqa.model.vo;

import edu.zjut.traceqa.model.po.User;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员视角的用户信息 DTO。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserVO {

    private Long id;

    private String username;

    private String nickname;

    private String roleCode;

    private Integer status;

    private LocalDateTime createTime;

    /**
     * 由用户实体组装
     */
    public static AdminUserVO of(User user) {
        return new AdminUserVO(user.getId(), user.getUsername(), user.getNickname(),
                user.getRoleCode(), user.getStatus(), user.getCreateTime());
    }
}
