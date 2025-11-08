package com.example.cherrydapp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.tx.RawTransactionManager;
import org.springframework.beans.factory.annotation.Value;
import java.util.Optional;
import java.time.Duration;
import java.time.Instant;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EvmService {

    private final Web3j web3j;
    private final RawTransactionManager txManager;
    private final Credentials credentials;

    // RPC URL 표기를 위해 application.yml 에 web3.rpcUrl 을 주입 (없으면 unknown), Sepolia 사용
    @Value("${web3.rpcUrl:unknown}")
    private String rpcUrl;

    /* -------------------- 네트워크/기본 조회 -------------------- */

    /**
     * 연결된 RPC URL을 그대로 노출 (yml 로 주입)
     */
    public String getRpcUrl() {
        return rpcUrl;
    }

    /**
     * 클라이언트 버전 문자열 반환 (예: Sepolia/Erigon ...)
     */
    public String getClientVersion() {
        try {
            var v = web3j.web3ClientVersion().send();
            return (v != null && v.getWeb3ClientVersion() != null) ? v.getWeb3ClientVersion() : "";
        } catch (Exception e) {
            return "";
        }
    }
    public String getFromAddress() {
        return credentials.getAddress();
    }

    /* -------------------- Convenience DTOs -------------------- */

    /** ThirtyOneGame 상태 한 번에 돌려주기 위한 레코드 */
    public record T31State(BigInteger round, BigInteger pot) {}

    /** ERC-20 메타데이터(name/symbol/decimals) 패키징용 레코드 */
    public record Erc20Meta(String name, String symbol, int decimals) {}

    /** T31 상태를 한 번에 조회 */
    public T31State t31State(String contract) throws Exception {
        BigInteger round = t31CurrentRound(contract);
        BigInteger pot   = t31PotSmart(contract);
        return new T31State(round, pot);
    }

    /** ERC-20 메타데이터를 한 번에 조회 */
    public Erc20Meta erc20Meta(String token) throws Exception {
        return new Erc20Meta(erc20Name(token), erc20Symbol(token), erc20Decimals(token));
    }

    /** 토큰 주소를 기반으로 사람이 읽는 단위를 raw로 변환 (decimals 자동 조회) */
    public BigInteger toRawByToken(String token, BigDecimal humanAmount) throws Exception {
        int dec = erc20Decimals(token);
        return toRaw(humanAmount, dec);
    }

    /** 토큰 주소를 기반으로 raw 값을 사람이 읽는 단위로 변환 (decimals 자동 조회) */
    public BigDecimal toHumanByToken(String token, BigInteger rawAmount) throws Exception {
        int dec = erc20Decimals(token);
        return toHuman(rawAmount, dec);
    }

    public boolean isConnected() {
        try {
            var v = web3j.web3ClientVersion().send();
            return v != null && v.getWeb3ClientVersion() != null && !v.getWeb3ClientVersion().isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    public BigInteger getBlockNumber() {
        try {
            return web3j.ethBlockNumber().send().getBlockNumber();
        } catch (Exception e) {
            return BigInteger.valueOf(-1);
        }
    }

    // ✅ 수정: getChainId()는 BigInteger 반환 → 16진 문자열로 변환
    public String getChainIdHex() {
        try {
            BigInteger id = web3j.ethChainId().send().getChainId();
            return "0x" + id.toString(16); // 0xaa36a7 == Sepolia (11155111)
        } catch (Exception e) {
            return "0x0";
        }
    }

    public BigInteger getEthBalanceWei(String address) throws Exception {
        return web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST).send().getBalance();
    }

    public BigDecimal getEthBalanceEther(String address) throws Exception {
        BigInteger wei = getEthBalanceWei(address);
        return new BigDecimal(wei).movePointLeft(18);
    }

    /* -------------------- ERC-20 읽기 -------------------- */

    public int erc20Decimals(String token) throws Exception {
        Function f = new Function("decimals", List.of(), List.of(new TypeReference<Uint8>() {}));
        List<Type> out = ethCall(token, f);
        return ((Uint8) out.get(0)).getValue().intValue();
    }

    public BigInteger erc20BalanceOf(String token, String owner) throws Exception {
        Function f = new Function("balanceOf", List.of(new Address(owner)), List.of(new TypeReference<Uint256>() {}));
        List<Type> out = ethCall(token, f);
        return (BigInteger) out.get(0).getValue();
    }

    public BigInteger erc20Allowance(String token, String owner, String spender) throws Exception {
        Function f = new Function("allowance",
                List.of(new Address(owner), new Address(spender)),
                List.of(new TypeReference<Uint256>() {}));
        List<Type> out = ethCall(token, f);
        return (BigInteger) out.get(0).getValue();
    }

    public String erc20Symbol(String token) throws Exception {
        Function f = new Function("symbol", List.of(), List.of(new TypeReference<Utf8String>() {}));
        List<Type> out = ethCall(token, f);
        return (String) out.get(0).getValue();
    }

    public String erc20Name(String token) throws Exception {
        Function f = new Function("name", List.of(), List.of(new TypeReference<Utf8String>() {}));
        List<Type> out = ethCall(token, f);
        return (String) out.get(0).getValue();
    }

    /* -------------------- ERC-20 쓰기(approve) -------------------- */

    public String erc20Approve(String token, String spender, BigInteger rawAmount) throws Exception {
        Function f = new Function("approve",
                List.of(new Address(spender), new Uint256(rawAmount)),
                List.of(new TypeReference<Bool>() {}));
        return sendFunctionTx(token, f);
    }

    public String erc20Transfer(String token, String to, BigInteger rawAmount) throws Exception {
        Function f = new Function("transfer",
                List.of(new Address(to), new Uint256(rawAmount)),
                List.of(new TypeReference<Bool>() {}));
        return sendFunctionTx(token, f);
    }

    public String erc20TransferFrom(String token, String from, String to, BigInteger rawAmount) throws Exception {
        Function f = new Function("transferFrom",
                List.of(new Address(from), new Address(to), new Uint256(rawAmount)),
                List.of(new TypeReference<Bool>() {}));
        return sendFunctionTx(token, f);
    }

    /* -------------------- SimpleWallet 입출금 -------------------- */

    public String depositErc20(String simpleWallet, String token, BigInteger rawAmount) throws Exception {
        Function f = new Function("depositErc20",
                List.of(new Address(token), new Uint256(rawAmount)),
                List.of());
        return sendFunctionTx(simpleWallet, f);
    }

    public String withdrawErc20(String simpleWallet, String token, BigInteger rawAmount) throws Exception {
        Function f = new Function("withdrawErc20",
                List.of(new Address(token), new Uint256(rawAmount)),
                List.of());
        return sendFunctionTx(simpleWallet, f);
    }

    public Optional<TransactionReceipt> waitForReceipt(String txHash, long timeoutMillis, long pollMillis) throws Exception {
        Instant end = Instant.now().plus(Duration.ofMillis(timeoutMillis));
        while (Instant.now().isBefore(end)) {
            EthGetTransactionReceipt r = web3j.ethGetTransactionReceipt(txHash).send();
            if (r.getTransactionReceipt().isPresent()) {
                return r.getTransactionReceipt();
            }
            Thread.sleep(pollMillis);
        }
        return Optional.empty();
    }

    public boolean isMinedAndSuccessful(String txHash, long timeoutMillis, long pollMillis) throws Exception {
        Optional<TransactionReceipt> opt = waitForReceipt(txHash, timeoutMillis, pollMillis);
        if (opt.isEmpty()) return false;
        TransactionReceipt rcpt = opt.get();
        return rcpt.isStatusOK();
    }

    /* -------------------- 단위 변환 -------------------- */

    public BigInteger toRaw(BigDecimal human, int decimals) {
        return human.movePointRight(decimals).toBigIntegerExact();
    }

    public BigDecimal toHuman(BigInteger raw, int decimals) {
        return new BigDecimal(raw).movePointLeft(decimals);
    }

    /* -------------------- ThirtyOneGame (best-effort) -------------------- */

    /**
     * submit(uint256 guess) 를 가정하고 트랜잭션 전송
     */
    public String t31Submit(String contract, BigInteger guess) throws Exception {
        Function f = new Function(
                "submit",
                List.of(new Uint256(guess)),
                List.of()
        );
        return sendFunctionTx(contract, f);
    }

    /**
     * view 함수 예: currentRound() returns (uint256)
     */
    public BigInteger t31CurrentRound(String contract) throws Exception {
        Function f = new Function("currentRound", List.of(), List.of(new TypeReference<Uint256>() {}));
        List<Type> out = ethCall(contract, f);
        return (BigInteger) out.get(0).getValue();
    }

    /**
     * view 함수 예: pot() returns (uint256) 혹은 getBalance()
     */
    public BigInteger t31Pot(String contract) throws Exception {
        // 우선 pot() 시도, 실패 시 getBalance() 시그니처를 재시도
        try {
            Function f = new Function("pot", List.of(), List.of(new TypeReference<Uint256>() {}));
            List<Type> out = ethCall(contract, f);
            return (BigInteger) out.get(0).getValue();
        } catch (Exception e) {
            Function f2 = new Function("getBalance", List.of(), List.of(new TypeReference<Uint256>() {}));
            List<Type> out2 = ethCall(contract, f2);
            return (BigInteger) out2.get(0).getValue();
        }
    }

    /**
     * pot 조회를 보다 안전하게 수행:
     *  1) 무인자 pot() 시도
     *  2) getBalance() 시도
     *  3) 현재 round를 읽어 pot(uint256)/getPot(uint256)/potOf(uint256)/pool(uint256)/poolOf(uint256) 순차 시도
     *  실패 시 0으로 폴백 (API 500 방지)
     */
    public BigInteger t31PotSmart(String contract) throws Exception {
        // 1) pot()
        try {
            Function f = new Function("pot", List.of(), List.of(new TypeReference<Uint256>() {}));
            List<Type> out = ethCall(contract, f);
            if (!out.isEmpty()) return (BigInteger) out.get(0).getValue();
        } catch (Exception ignore) {}

        // 2) getBalance()
        try {
            Function f2 = new Function("getBalance", List.of(), List.of(new TypeReference<Uint256>() {}));
            List<Type> out2 = ethCall(contract, f2);
            if (!out2.isEmpty()) return (BigInteger) out2.get(0).getValue();
        } catch (Exception ignore) {}

        // 3) round 인자 필요 버전들
        BigInteger round = t31CurrentRound(contract);
        String[] candidates = {"pot", "getPot", "potOf", "pool", "poolOf"};
        for (String name : candidates) {
            try {
                Function f3 = new Function(
                        name,
                        List.of(new Uint256(round)),
                        List.of(new TypeReference<Uint256>() {})
                );
                List<Type> out3 = ethCall(contract, f3);
                if (!out3.isEmpty()) return (BigInteger) out3.get(0).getValue();
            } catch (Exception ignore) {}
        }

        // 4) 실패 시 graceful fallback
        return BigInteger.ZERO;
    }

    /**
     * 현재 라운드가 열려있는지 추정: isOpen()/isRoundOpen()/isActive()/isRunning()/open() 순차 시도.
     * 전부 실패 시 null 반환(정보 없음 의미).
     */
    public Boolean t31IsOpenSmart(String contract) throws Exception {
        String[] names = {"isOpen", "isRoundOpen", "isActive", "isRunning", "open"};
        for (String name : names) {
            try {
                Function f = new Function(name, List.of(), List.of(new TypeReference<Bool>() {}));
                List<Type> out = ethCall(contract, f);
                if (!out.isEmpty()) {
                    Object v = out.get(0).getValue();
                    if (v instanceof Boolean b) return b;
                }
            } catch (Exception ignore) {}
        }
        return null; // 알 수 없음
    }

    /**
     * 승자 주소 조회 추정: winner()/getWinner()/lastWinner() → 실패 시 winnerOf(currentRound) 등 순차 시도.
     * 정보 없으면 null.
     */
    public String t31WinnerSmart(String contract) throws Exception {
        String[] noArg = {"winner", "getWinner", "lastWinner"};
        for (String n : noArg) {
            try {
                Function f = new Function(n, List.of(), List.of(new TypeReference<Address>() {}));
                List<Type> out = ethCall(contract, f);
                if (!out.isEmpty()) return out.get(0).getValue().toString();
            } catch (Exception ignore) {}
        }
        // winnerOf(round) / getWinnerOf(round)
        try {
            BigInteger round = t31CurrentRound(contract);
            String[] withArg = {"winnerOf", "getWinnerOf"};
            for (String n : withArg) {
                try {
                    Function f = new Function(n, List.of(new Uint256(round)), List.of(new TypeReference<Address>() {}));
                    List<Type> out = ethCall(contract, f);
                    if (!out.isEmpty()) return out.get(0).getValue().toString();
                } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {}
        return null;
    }

    /**
     * 다음 라운드 시작 트랜잭션 추정: start()/startNextRound()/newRound()/openRound() 순차 시도.
     * (주의: onlyOwner 제약이 있으면 리버트될 수 있음)
     */
    public String t31StartNextRoundSmart(String contract) throws Exception {
        String[] txNames = {"start", "startNextRound", "newRound", "openRound"};
        for (String name : txNames) {
            try {
                Function f = new Function(name, List.of(), List.of());
                return sendFunctionTx(contract, f);
            } catch (Exception ignore) {}
        }
        throw new RuntimeException("No matching start function (start/startNextRound/newRound/openRound) on contract");
    }

    /* -------------------- 내부 헬퍼: call / send -------------------- */

    private List<Type> ethCall(String to, Function function) throws Exception {
        String data = FunctionEncoder.encode(function);
        var tx = Transaction.createEthCallTransaction(null, to, data);
        EthCall resp = web3j.ethCall(tx, DefaultBlockParameterName.LATEST).send();
        if (resp.hasError()) throw new RuntimeException(resp.getError().getMessage());
        return FunctionReturnDecoder.decode(resp.getValue(), function.getOutputParameters());
    }

    // ✅ 수정: RawTransaction1559 제거, 기본 gasPrice 기반 전송
    private String sendFunctionTx(String to, Function function) throws Exception {
        String data = FunctionEncoder.encode(function);

        // 가스 추정
        BigInteger gasLimit = estimateGas(credentials.getAddress(), to, data);

        // 기본 가스가격 가져오기
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

        // nonce
        BigInteger nonce = web3j.ethGetTransactionCount(
                credentials.getAddress(), DefaultBlockParameterName.PENDING).send().getTransactionCount();

        // RawTransaction 생성 (EIP-1559 대신 legacy 방식)
        RawTransaction rawTx = RawTransaction.createTransaction(
                nonce, gasPrice, gasLimit, to, BigInteger.ZERO, data);

        // 서명 및 전송
        String signed = txManager.sign(rawTx);
        EthSendTransaction sent = web3j.ethSendRawTransaction(signed).send();

        if (sent.hasError()) throw new RuntimeException(sent.getError().getMessage());
        return sent.getTransactionHash();
    }

    private BigInteger estimateGas(String from, String to, String data) throws Exception {
        Transaction tx = Transaction.createFunctionCallTransaction(
                from,
                null, // nonce (노드가 추정 시 불필요)
                null, // gasPrice
                null, // gasLimit
                to,
                BigInteger.ZERO,
                data
        );
        var ethEstimate = web3j.ethEstimateGas(tx).send();
        if (ethEstimate.hasError() || ethEstimate.getAmountUsed() == null) {
            return BigInteger.valueOf(300_000);
        }
        return ethEstimate.getAmountUsed().multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
    }

    private BigInteger getMaxPriority() throws Exception {
        try {
            var tip = web3j.ethMaxPriorityFeePerGas().send().getMaxPriorityFeePerGas();
            return tip != null ? tip : BigInteger.valueOf(30_000_000_000L); // 30 gwei fallback
        } catch (Exception e) {
            return BigInteger.valueOf(30_000_000_000L);
        }
    }

    private BigInteger getBaseFeeFallback() throws Exception {
        try {
            var block = web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock();
            if (block != null && block.getBaseFeePerGas() != null) {
                return block.getBaseFeePerGas();
            }
        } catch (Exception ignore) {}
        // fallback 30 gwei
        return BigInteger.valueOf(30_000_000_000L);
    }

    /* -------------------- Convenience -------------------- */
    public String connectionSummary() {
        boolean ok = isConnected();
        String ver = getClientVersion();
        return String.format("connected=%s, url=%s, client=%s", ok, rpcUrl, ver);
    }
}