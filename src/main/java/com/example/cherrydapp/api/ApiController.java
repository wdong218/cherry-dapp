package com.example.cherrydapp.api;

import com.example.cherrydapp.service.EvmService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Objects;

@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ApiController {

    private final EvmService evm;

    @Value("${web3.rpcUrl:unknown}")
    private String rpcUrl;

    @Value("${web3.chain-id:11155111}")
    private long cfgChainId;

    // (선택) 기본 주소를 properties로 박아뒀다면 자동 주입
    @Value("${cherry.token.address:}")
    private String cherryToken;

    @Value("${simple.wallet.address:0x428dc0f4f806054CE70b26F1bB6a186317644123}")
    private String simpleWallet;

    /* ---- 네트워크 ---- */

    @GetMapping("/health/network")
    public Map<String, Object> health() {
        String maskedUrl = rpcUrl;
        try {
            int idx = (rpcUrl != null) ? rpcUrl.lastIndexOf('/') : -1;
            if (idx >= 0 && idx < rpcUrl.length() - 1) {
                String key = rpcUrl.substring(idx + 1);
                String head = key.length() > 6 ? key.substring(0, 6) : "";
                maskedUrl = rpcUrl.substring(0, idx + 1) + head + "****";
            }
        } catch (Exception ignore) {}

        String rpcHost = "";
        try {
            rpcHost = new URI(Objects.toString(rpcUrl, "")).getHost();
            if (rpcHost == null) rpcHost = "";
        } catch (Exception ignore) {}

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("connected", evm.isConnected());
        out.put("rpcUrlMasked", Objects.toString(maskedUrl, ""));
        out.put("rpcHost", Objects.toString(rpcHost, ""));
        out.put("cfgChainId", cfgChainId);
        String chainIdHex = evm.getChainIdHex();
        out.put("chainIdHex", chainIdHex != null ? chainIdHex : "");
        return out;
    }

    @GetMapping({"/account", "/health/account"})
    public Map<String, Object> account() {
        return Map.of("address", evm.getFromAddress());
    }

    @GetMapping("/block-number")
    public Map<String, Object> blockNumber() {
        return Map.of("blockNumber", evm.getBlockNumber());
    }

    /* ---- 잔액/조회 ---- */

    @GetMapping("/balance/eth")
    public Map<String, Object> eth(@RequestParam String address) throws Exception {
        BigInteger wei = evm.getEthBalanceWei(address);
        BigDecimal eth = new BigDecimal(wei).movePointLeft(18);
        return Map.of("address", address, "wei", wei.toString(), "eth", eth.toPlainString());
    }

    @GetMapping("/erc20/decimals")
    public Map<String, Object> decimals(@RequestParam String token) throws Exception {
        return Map.of("token", token, "decimals", evm.erc20Decimals(token));
    }

    @GetMapping({"/balance/erc20", "/erc20/balance"})
    public Map<String, Object> erc20Bal(@RequestParam String token, @RequestParam String address) throws Exception {
        int dec = evm.erc20Decimals(token);
        BigInteger raw = evm.erc20BalanceOf(token, address);
        BigDecimal human = evm.toHuman(raw, dec);
        return Map.of("token", token, "address", address, "raw", raw.toString(), "decimals", dec, "human", human.toPlainString());
    }

    @GetMapping("/erc20/allowance")
    public Map<String, Object> allowance(@RequestParam String token, @RequestParam String owner, @RequestParam String spender) throws Exception {
        int dec = evm.erc20Decimals(token);
        BigInteger raw = evm.erc20Allowance(token, owner, spender);
        BigDecimal human = evm.toHuman(raw, dec);
        return Map.of("token", token, "owner", owner, "spender", spender, "raw", raw.toString(), "decimals", dec, "human", human.toPlainString());
    }

    @GetMapping("/erc20/meta")
    public Map<String, Object> erc20Meta(@RequestParam String token) throws Exception {
        var meta = evm.erc20Meta(token);
        return Map.of(
                "token", token,
                "name", meta.name(),
                "symbol", meta.symbol(),
                "decimals", meta.decimals()
        );
    }

    /* ---- approve / deposit / withdraw ---- */

    @PostMapping("/erc20/approve")
    public Map<String, Object> approve(
            @RequestParam String token,
            @RequestParam String spender,
            @RequestParam BigDecimal amountHuman) throws Exception {

        BigInteger raw = evm.toRawByToken(token, amountHuman);
        String tx = evm.erc20Approve(token, spender, raw);
        return Map.of(
                "txHash", tx,
                "explorer", "https://sepolia.etherscan.io/tx/" + tx,
                "token", token, "spender", spender, "amountRaw", raw.toString(), "amountHuman", amountHuman.toPlainString());
    }

    @PostMapping("/erc20/transfer")
    public Map<String, Object> transfer(
            @RequestParam String token,
            @RequestParam String to,
            @RequestParam BigDecimal amountHuman) throws Exception {
        BigInteger raw = evm.toRawByToken(token, amountHuman);
        String tx = evm.erc20Transfer(token, to, raw);
        return Map.of(
                "txHash", tx,
                "explorer", "https://sepolia.etherscan.io/tx/" + tx,
                "token", token, "to", to, "amountRaw", raw.toString(), "amountHuman", amountHuman.toPlainString());
    }

    @PostMapping("/erc20/transferFrom")
    public Map<String, Object> transferFrom(
            @RequestParam String token,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amountHuman) throws Exception {
        BigInteger raw = evm.toRawByToken(token, amountHuman);
        String tx = evm.erc20TransferFrom(token, from, to, raw);
        return Map.of(
                "txHash", tx,
                "explorer", "https://sepolia.etherscan.io/tx/" + tx,
                "token", token, "from", from, "to", to, "amountRaw", raw.toString(), "amountHuman", amountHuman.toPlainString());
    }

    @PostMapping("/wallet/deposit")
    public Map<String, Object> deposit(
            @RequestParam String token,
            @RequestParam BigDecimal amountHuman,
            @RequestParam(required = false) String wallet // 미지정 시 기본값
    ) throws Exception {
        String targetWallet = (wallet == null || wallet.isBlank()) ? simpleWallet : wallet;
        BigInteger raw = evm.toRawByToken(token, amountHuman);
        String tx = evm.depositErc20(targetWallet, token, raw);
        return Map.of(
                "txHash", tx,
                "explorer", "https://sepolia.etherscan.io/tx/" + tx,
                "wallet", targetWallet, "token", token, "amountRaw", raw.toString(), "amountHuman", amountHuman.toPlainString());
    }

    @PostMapping("/wallet/withdraw")
    public Map<String, Object> withdraw(
            @RequestParam String token,
            @RequestParam BigDecimal amountHuman,
            @RequestParam(required = false) String wallet
    ) throws Exception {
        String targetWallet = (wallet == null || wallet.isBlank()) ? simpleWallet : wallet;
        BigInteger raw = evm.toRawByToken(token, amountHuman);
        String tx = evm.withdrawErc20(targetWallet, token, raw);
        return Map.of(
                "txHash", tx,
                "explorer", "https://sepolia.etherscan.io/tx/" + tx,
                "wallet", targetWallet, "token", token, "amountRaw", raw.toString(), "amountHuman", amountHuman.toPlainString());
    }

    /* ---- ThirtyOneGame ---- */

    @GetMapping("/t31/state")
    public Map<String, Object> t31State(@RequestParam String contract) throws Exception {
        var st = evm.t31State(contract);
        return Map.of(
                "contract", contract,
                "round", st.round().toString(),
                "potRaw", st.pot().toString()
        );
    }

    @PostMapping("/t31/submit")
    public Map<String, Object> t31Submit(
            @RequestParam String contract,
            @RequestParam BigInteger guess) throws Exception {
        String tx = evm.t31Submit(contract, guess);
        return Map.of("contract", contract, "guess", guess.toString(), "txHash", tx, "explorer", "https://sepolia.etherscan.io/tx/" + tx);
    }

    /** T31 상세 상태 점검: round, potRaw, isOpen?, winner? 를 한 번에 */
    @GetMapping("/t31/inspect")
    public Map<String, Object> t31Inspect(@RequestParam String contract) throws Exception {
        var round  = evm.t31CurrentRound(contract);
        var pot    = evm.t31PotSmart(contract);
        var open   = evm.t31IsOpenSmart(contract);
        var winner = evm.t31WinnerSmart(contract);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("contract", contract);
        out.put("round", round.toString());
        out.put("potRaw", pot.toString());
        if (open != null) out.put("isOpen", open);
        if (winner != null) out.put("winner", winner);
        return out;
    }

    /** (옵션) 다음 라운드 시작 트랜잭션 — onlyOwner 제약이 있을 수 있음 */
    @PostMapping("/t31/round/next")
    public Map<String, Object> t31NextRound(@RequestParam String contract) throws Exception {
        String tx = evm.t31StartNextRoundSmart(contract);
        return Map.of(
                "contract", contract,
                "txHash", tx,
                "explorer", "https://sepolia.etherscan.io/tx/" + tx
        );
    }
}