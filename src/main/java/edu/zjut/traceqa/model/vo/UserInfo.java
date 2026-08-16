package edu.zjut.traceqa.model.vo;
import edu.zjut.traceqa.model.po.User;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前用户信息 DTO。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserInfo {

    private Long userId;

    private String username;

    private String nickname;

    private String roleCode;

    private Integer status;

    private List<String> permissions;

/** 由用户实体与权限码集合组装 */
    public static UserInfo of(User user, List<String> permissions) {
        return new UserInfo(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRoleCode(),
                user.getStatus(),
                permissions
        );
    }
}
