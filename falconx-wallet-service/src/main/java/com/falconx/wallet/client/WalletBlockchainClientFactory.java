package com.falconx.wallet.client;

import com.falconx.wallet.config.WalletServiceProperties;
import java.net.ConnectException;
import java.net.URI;
import java.util.Locale;
import org.p2p.solanaj.rpc.RpcClient;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.key.KeyPair;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.websocket.WebSocketService;

/**
 * 钱包链客户端工厂。
 *
 * <p>该工厂统一负责创建各条链对应的 SDK 客户端，避免监听器各自拼装连接参数。
 * 当前阶段只创建客户端，不在工厂层执行轮询或解析逻辑。
 */
public class WalletBlockchainClientFactory {

    /**
     * 创建 EVM 链客户端。
     *
     * @param rpcUrl 节点 RPC 地址
     * @return Web3j 客户端
     */
    public Web3j createEvmClient(URI rpcUrl) {
        return Web3j.build(createEvmService(rpcUrl));
    }

    /**
     * 按 URI 协议选择底层 EVM 传输实现。
     *
     * <p>Stage 6A 的以太坊联调既要支持本地 `http` 节点，也要支持
     * Alchemy 这类 `wss` 端点。这里统一在工厂层完成协议分派，
     * 避免监听器自行判断传输类型并产生多处配置分叉。
     *
     * @param rpcUrl 节点 RPC 地址
     * @return 对应的 Web3j Service
     */
    Web3jService createEvmService(URI rpcUrl) {
        String scheme = rpcUrl.getScheme();
        if (scheme == null || scheme.isBlank()) {
            throw new IllegalArgumentException("wallet.evm.rpcUrl scheme is required");
        }
        return switch (scheme.toLowerCase(Locale.ROOT)) {
            case "http", "https" -> new HttpService(rpcUrl.toString());
            case "ws", "wss" -> createWebSocketService(rpcUrl);
            default -> throw new IllegalArgumentException("wallet.evm.rpcUrl scheme is not supported: " + scheme);
        };
    }

    /**
     * 创建并主动建立 WebSocket 连接。
     *
     * <p>Web3j 的 `WebSocketService` 在真正发起 JSON-RPC 调用或订阅前必须先显式连接。
     * 若这里不提前连接，监听器启动阶段会把无效客户端带入后续链路，直到首次 RPC 调用才暴露配置问题。
     *
     * @param rpcUrl WebSocket RPC 地址
     * @return 已建立连接的 WebSocketService
     */
    WebSocketService createWebSocketService(URI rpcUrl) {
        WebSocketService webSocketService = new WebSocketService(rpcUrl.toString(), false);
        try {
            webSocketService.connect();
        } catch (ConnectException ex) {
            throw new IllegalStateException("wallet.evm.websocket.connect.failed url=" + rpcUrl, ex);
        }
        return webSocketService;
    }

    /**
     * 创建 Solana RPC 客户端。
     *
     * @param rpcUrl 节点 RPC 地址
     * @return Solana RPC 客户端
     */
    public RpcClient createSolanaClient(URI rpcUrl) {
        return new RpcClient(rpcUrl.toString());
    }

    /**
     * 创建 Tron gRPC 客户端。
     *
     * <p>Trident `ApiWrapper` 需要私钥才能构造。当前阶段只做链上只读监听骨架，
     * 因此使用临时生成的开发态私钥完成客户端初始化，不把真实业务密钥带入进程。
     *
     * @param chainProperties Tron 链配置
     * @return Tron ApiWrapper 客户端
     */
    public ApiWrapper createTronClient(WalletServiceProperties.Chain chainProperties) {
        String grpcEndpoint = toGrpcTarget(chainProperties.getRpcUrl());
        String grpcSolidityEndpoint = chainProperties.getSolidityRpcUrl() == null
                ? grpcEndpoint
                : toGrpcTarget(chainProperties.getSolidityRpcUrl());
        String privateKey = KeyPair.generate().toPrivateKey();
        if (chainProperties.getApiKey() == null || chainProperties.getApiKey().isBlank()) {
            return new ApiWrapper(grpcEndpoint, grpcSolidityEndpoint, privateKey);
        }
        return new ApiWrapper(grpcEndpoint, grpcSolidityEndpoint, privateKey, chainProperties.getApiKey());
    }

    private String toGrpcTarget(URI rpcUrl) {
        if (rpcUrl.getHost() == null) {
            return rpcUrl.toString();
        }
        int port = rpcUrl.getPort() > 0 ? rpcUrl.getPort() : 50051;
        return rpcUrl.getHost() + ":" + port;
    }
}
