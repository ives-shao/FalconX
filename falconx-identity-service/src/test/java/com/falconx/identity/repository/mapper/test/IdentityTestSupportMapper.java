package com.falconx.identity.repository.mapper.test;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * identity-service 测试专用 Mapper。
 *
 * <p>该 Mapper 只在测试源码中存在，用来完成 Stage 5 集成测试的清表和结果断言。
 */
@Mapper
public interface IdentityTestSupportMapper {

    default void clearOwnerTables() {
        deleteInbox();
        deleteRefreshTokenSession();
        deleteUser();
    }

    int deleteInbox();

    int deleteRefreshTokenSession();

    int deleteUser();

    Integer selectUserStatusById(@Param("userId") Long userId);

    Integer countRefreshTokenSession();

    Integer countUsedRefreshTokenSession();

    Integer countProcessedInboxByEventId(@Param("eventId") String eventId);
}
