package com.falconx.market.repository.mapper;

import com.falconx.market.repository.mapper.record.MarketOutboxRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * market-service Outbox MyBatis Mapper。
 *
 * <p>该 Mapper 只负责 `t_outbox` 的 SQL 声明，
 * 不承担市场事件的编排和状态转换逻辑。
 */
@Mapper
public interface MarketOutboxMapper {

    /**
     * 新增或覆盖一条 Outbox 记录。
     *
     * @param record Outbox 记录
     * @return 影响行数
     */
    int upsertMarketOutbox(MarketOutboxRecord record);

    /**
     * 查询当前可调度的发件箱批次。
     *
     * @param now 当前时间
     * @param limit 批次大小
     * @return 可调度记录列表
     */
    List<MarketOutboxRecord> selectDispatchableBatch(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 将一条消息标记为调度中。
     *
     * @param id 主键
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int updateDispatchingById(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 将一条消息标记为已发送。
     *
     * @param id 主键
     * @param sentAt 发送时间
     * @return 影响行数
     */
    int updateSentById(@Param("id") Long id, @Param("sentAt") LocalDateTime sentAt);

    /**
     * 将一条消息标记为失败或死信。
     *
     * @param id 主键
     * @param statusCode 状态码
     * @param retryCount 重试次数
     * @param nextRetryAt 下一次重试时间
     * @param lastError 最近一次错误
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int updateFailedById(@Param("id") Long id,
                         @Param("statusCode") Integer statusCode,
                         @Param("retryCount") Integer retryCount,
                         @Param("nextRetryAt") LocalDateTime nextRetryAt,
                         @Param("lastError") String lastError,
                         @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 按主键查询 Outbox 记录。
     *
     * @param id 主键
     * @return Outbox 记录
     */
    MarketOutboxRecord selectById(@Param("id") Long id);
}
