package com.falconx.identity.repository.mapper;

import com.falconx.identity.repository.mapper.record.IdentityUserRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * identity 用户 MyBatis Mapper。
 *
 * <p>该 Mapper 负责 `falconx_identity.t_user` 的 SQL 声明，
 * Repository 会在其上层完成领域对象与记录对象的转换。
 */
@Mapper
public interface IdentityUserMapper {

    /**
     * 按邮箱查询用户记录。
     *
     * @param email 归一化邮箱
     * @return 用户记录
     */
    IdentityUserRecord selectByEmail(@Param("email") String email);

    /**
     * 按主键查询用户记录。
     *
     * @param id 用户主键
     * @return 用户记录
     */
    IdentityUserRecord selectById(@Param("id") Long id);

    /**
     * 插入新用户。
     *
     * @param record 用户持久化记录
     * @return 影响行数
     */
    int insertIdentityUser(IdentityUserRecord record);

    /**
     * 更新已有用户。
     *
     * @param record 用户持久化记录
     * @return 影响行数
     */
    int updateIdentityUser(IdentityUserRecord record);
}
