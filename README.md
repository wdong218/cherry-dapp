# Cherry DApp

Spring Boot + Web3j 기반의 Ethereum dApp 백엔드 프로젝트.  
Sepolia 테스트넷을 기반으로 네트워크 연결, ERC-20 토큰 상호작용,  
SimpleWallet 입출금, ThirtyOneGame(베스킨라빈스31 게임) 컨트랙트와의 상호작용 기능을 구현.

---

## 📁 프로젝트 구조

cherry-dapp/  
├── src/  
│   ├── main/java/com/example/cherrydapp/  
│   │   ├── api/ApiController.java           # REST API 진입점  
│   │   ├── cli/ConsoleMenu.java             # CLI 메뉴 실행  
│   │   ├── config/Web3Config.java           # Web3j 설정  
│   │   ├── service/EvmService.java          # 핵심 로직 (Web3 호출)  
│   │   └── CherryDappApplication.java       # Spring Boot entrypoint  
│   └── resources/  
│       ├── application.properties  
│       ├── application-cli.properties  
│       ├── static/t31.html                  # ThirtyOneGame FE  
│       └── static/balances.html             # ERC-20 조회 FE  
├── build.gradle  
└── settings.gradle  

---

📘 결과 정리:
프로젝트 수행 과정 및 실행 결과는 Notion에 정리되어 있습니다.  
🔗 [📄 Notion 바로가기](https://www.notion.so/11-02-2a526275352480099271e908659f83a8?source=copy_link)
