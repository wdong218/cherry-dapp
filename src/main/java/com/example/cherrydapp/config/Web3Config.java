package com.example.cherrydapp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;


@Configuration
public class Web3Config {

    // Sepolia RPC endpoint (Alchemy)
    @Value("${web3.rpcUrl:unknown}")
    private String rpcUrl;

    @Value("${wallet.private-key}")
    private String privateKey;

    @Value("${web3.chain-id:11155111}")
    private long chainId;

    @Bean
    public Web3j web3j() {
        return Web3j.build(new HttpService(rpcUrl));
    }

    @Bean
    public Credentials credentials() {
        return Credentials.create(privateKey);
    }

    @Bean
    public RawTransactionManager txManager(Web3j web3j, Credentials credentials) {
        // 체인ID를 명시해 EIP-155 리플레이 보호
        return new RawTransactionManager(web3j, credentials, chainId);
    }
}