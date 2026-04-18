package com.falconx.market.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * market-service 测试数据库初始化器。
 *
 * <p>market 集成测试统一使用标准测试库 `falconx_market_it`。
 * 该初始化器在 Spring 上下文启动前重建测试库，确保：
 *
 * <ul>
 *   <li>不再依赖历史临时库名 `falconx_market_it_stage6a`</li>
 *   <li>旧测试库中的 Flyway checksum 残留不会污染当前测试</li>
 *   <li>上下文启动后直接走当前 migration 的标准迁移路径</li>
 * </ul>
 */
public class MarketTestDatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String TEST_DATABASE = "falconx_market_it";
    private static final String ADMIN_JDBC_URL =
            "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        recreateDatabase();
    }

    private void recreateDatabase() {
        try (Connection connection = DriverManager.getConnection(ADMIN_JDBC_URL, "root", "root");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + TEST_DATABASE);
            statement.execute(
                    "CREATE DATABASE " + TEST_DATABASE + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to recreate market test database " + TEST_DATABASE, exception);
        }
    }
}
