package com.example.cherrydapp.cli;

import com.example.cherrydapp.service.EvmService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Scanner;

@Component
@Profile("cli")
@RequiredArgsConstructor
public class ConsoleMenu implements CommandLineRunner {

    private final EvmService evm;
    // System.in 은 애플리케이션 전체에서 공유되므로 절대 close 하지 말 것
    private final Scanner in = new Scanner(System.in);

    private String ask(String prompt) {
        System.out.print(prompt);
        if (!in.hasNextLine()) {
            System.out.println();
            System.out.println("입력이 종료되었습니다. 프로그램을 마칩니다.");
            System.exit(0); // 정상 종료 처리
        }
        return in.nextLine().trim();
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== CherryDapp CLI ===");
        System.out.println("지갑: " + evm.getFromAddress());
        System.out.println("체인ID(hex): " + evm.getChainIdHex());
        System.out.println();

        while (true) {
            printMenu();
            String sel = ask("번호 선택 > ");
            try {
                switch (sel) {
                    case "1" -> actionT31State();
                    case "2" -> actionT31Submit();
                    case "3" -> actionErc20Balance();
                    case "4" -> actionEthBalance();
                    case "5", "q", "Q", "exit" -> { System.out.println("bye!"); return; }
                    default -> System.out.println("1~5 중에서 선택하세요.");
                }
            } catch (Exception e) {
                System.out.println("[에러] " + e.getMessage());
            }
            System.out.println();
        }
    }

    private void printMenu() {
        System.out.println("--------------------------------");
        System.out.println("1) ThirtyOneGame 상태 조회 (round, pot)");
        System.out.println("2) ThirtyOneGame submit (guess)");
        System.out.println("3) ERC-20 잔액 조회 (token, address)");
        System.out.println("4) ETH 잔액 조회 (address)");
        System.out.println("5) 종료");
        System.out.println("--------------------------------");
    }

    private void actionT31State() {
        String c = ask("T31 컨트랙트 주소: ");
        try {
            BigInteger round = evm.t31CurrentRound(c);
            BigInteger pot   = evm.t31PotSmart(c); // ← 안전한 버전으로 변경
            System.out.println("round=" + round + ", potRaw=" + pot);
        } catch (Exception e) {
            System.out.println("[에러] 조회 실패: " + e.getMessage());
            System.out.println("round=0, potRaw=0");
        }
    }

    private void actionT31Submit() throws Exception {
        String c = ask("T31 컨트랙트 주소: ");
        String g = ask("guess(정수): ");
        BigInteger guess = new BigInteger(g);
        String tx = evm.t31Submit(c, guess);
        System.out.println("txHash=" + tx);
    }

    private void actionErc20Balance() throws Exception {
        String token = ask("토큰 주소(token): ");
        String addr  = ask("조회할 계정 주소(address): ");
        int dec = evm.erc20Decimals(token);
        BigInteger raw = evm.erc20BalanceOf(token, addr);
        BigDecimal human = evm.toHuman(raw, dec);
        System.out.println("balance raw=" + raw + ", human=" + human + " (decimals=" + dec + ")");
    }

    private void actionEthBalance() throws Exception {
        String addr = ask("조회할 계정 주소(address): ");
        BigInteger wei = evm.getEthBalanceWei(addr);
        BigDecimal eth = new BigDecimal(wei).movePointLeft(18);
        System.out.println("ETH=" + eth.toPlainString() + " (" + wei + " wei)");
    }
}
