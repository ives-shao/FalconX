package com.falconx.trading.repository.mapper;

import com.falconx.trading.repository.mapper.record.TradingInboxRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 交易 Inbox MyBatis Mapper。
 *
 * <p>该 Mapper 只负责 `t_inbox` 的 SQL 声明，用于低频关键事件去重。
 */
@Mapper
public interface TradingInboxMapper {

    /**
     * 若事件不存在则插入一条已处理记录。
     *
     * @param record inbox 记录
     * @return 影响行数；1 表示首次写入，0 表示重复事件
     */
    int insertProcessedIfAbsent(TradingInboxRecord record);
}
