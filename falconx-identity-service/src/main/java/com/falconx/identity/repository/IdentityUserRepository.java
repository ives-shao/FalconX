package com.falconx.identity.repository;

import com.falconx.identity.entity.IdentityUser;
import java.util.Optional;

/**
 * 用户仓储抽象。
 *
 * <p>该仓储负责 identity owner 用户事实的读写。
 * 当前正式实现必须通过 `MyBatis Mapper + XML + Repository` 链路访问数据库。
 */
public interface IdentityUserRepository {

    /**
     * 按邮箱查询用户。
     *
     * @param email 归一化邮箱
     * @return 用户记录
     */
    Optional<IdentityUser> findByEmail(String email);

    /**
     * 按主键查询用户。
     *
     * @param userId 用户主键 ID
     * @return 用户记录
     */
    Optional<IdentityUser> findById(long userId);

    /**
     * 保存或覆盖用户记录。
     *
     * @param user 用户对象
     * @return 持久化后的用户对象
     */
    IdentityUser save(IdentityUser user);
}
