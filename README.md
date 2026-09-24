# restaurant-ops · AI 店长台（DSH Java Native 插件场景案例 P53）

> 基于 **deepseek-harness-java（DSH）Java Native 插件机制** 的餐饮门店经营场景案例：桌台看板（8 桌 4 状态）+ 菜品销量毛利（10 道菜含成本/库存）+ 开台点单（校验桌台空闲与菜品库存）+ 结账清台 + 午市经营分析（流水/翻台率/毛利率/库存预警），通过 `restaurant-copilot` 插件接入 AI 助手，支持自然语言看台、点菜、结账、给经营建议。

![总览](docs/images/01-overview.png)

## 一、项目组成

| 模块 | 说明 |
|------|------|
| `r-app` | Spring Boot 3.2 应用（端口 **18092**），餐厅经营 REST API 与前端页面 |
| `r-plugin` | DSH Java Native 插件（`restaurant-copilot`），打包 5 个 AI 工具 |

业务数据：8 张桌台（大厅/包间/窗边，空闲/就餐中/待清台/预订 4 状态）、10 道菜（含成本价、毛利率、今日销量、库存、标签）、3 笔初始订单，经营分析自动计算流水/翻台率/毛利率/库存预警。

## 二、插件工具（5 个）

| 工具 | 说明 |
|------|------|
| `table_board` | 桌台看板：桌名/座位/区域/状态/当前订单 |
| `menu` | 菜单：售价/成本/毛利率/销量/库存/标签 |
| `order_create` | 开台点单：校验桌台空闲 + 菜品库存，返回订单号与合计 |
| `checkout` | 结账：订单结账，桌台转「待清台」 |
| `analysis` | 经营分析：流水/翻台率/毛利率/桌态分布/爆款/库存预警 |

## 三、REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/tables` | 桌台看板 |
| GET | `/api/dishes` | 菜品列表 |
| GET | `/api/orders` | 订单列表 |
| POST | `/api/order` | 下单 `{tableId, items:[{dishId,qty}], note}` |
| POST | `/api/order/checkout` | 结账 `{orderId}` |
| GET | `/api/analysis` | 经营分析 |
| POST | `/api/assistant/stream` | AI 助手 SSE（透传 DSH） |

## 四、快速开始

```bash
mvn clean package -DskipTests
java -Dserver.port=18092 -jar r-app/target/r-app-1.0.0-SNAPSHOT.jar

bash install_plugin.sh r-plugin/target/r-plugin-1.0.0-SNAPSHOT.jar \
  restaurant-copilot 1.0.0-SNAPSHOT r-plugin-1.0.0-SNAPSHOT.jar "AI 店长助手"

open http://127.0.0.1:18092/
```

## 五、端到端验证

```bash
bash agent_stream.sh 127.0.0.1:8090 restaurant-copilot "看一下当前所有桌台的状态，哪些空着"
bash agent_stream.sh 127.0.0.1:8090 restaurant-copilot "推荐几道利润率高且还有库存的热菜"
bash agent_stream.sh 127.0.0.1:8090 restaurant-copilot "5 号桌来两位客人，帮忙开台点一份蟹粉小笼和一份桂花米酒"
bash agent_stream.sh 127.0.0.1:8090 restaurant-copilot "5号桌吃完了，帮我结账"
bash agent_stream.sh 127.0.0.1:8090 restaurant-copilot "看看今天的经营分析，有什么要注意的"
```

5 个工具全部验证通过。验证截图：

| 截图 | 内容 |
|------|------|
| ![AI 经营分析](docs/images/02-ai-analysis.png) | AI 解读午市流水/翻台率/库存预警并给动作建议 |
| ![AI 开台点菜](docs/images/03-ai-order.png) | AI 校验桌台空闲与库存后推荐菜品并确认下单 |
| ![AI 补货判断](docs/images/04-ai-stock.png) | AI 按销量与库存给补货优先级排序 |

## 六、技术要点

- **桌态机**：空闲 → 就餐中 → 待清台 → 空闲，预订为独立状态；下单前强制校验桌台为「空闲」，结账后自动转「待清台」。
- **库存校验**：点单时逐菜校验库存充足才可下单（酸梅汤售罄会被 AI 主动拦截并推荐替代品）。
- **毛利模型**：毛利率 =（售价-成本)/售价；经营分析给出爆款榜（销量×毛利双高）与库存预警（售罄/偏低分档）。
- **结论约束**：AI 回答固定动作导向——先报数据，再给可执行建议（补货/清台/主推），数据全部来自工具返回。

## 七、目录结构

```
restaurant-ops/
├── pom.xml                  # 父 pom（maven.compiler.parameters=true）
├── r-app/                   # Spring Boot 应用 (18092)
│   └── src/main/java/cn/xiaofuge/r/app/
│       ├── RestaurantApplication.java
│       ├── RStore.java        # 桌台/菜品/订单/经营分析
│       ├── RController.java   # REST API
│       └── AssistantController.java # SSE 透传 DSH
├── r-plugin/                # DSH 插件 (restaurant-copilot)
│   └── src/main/
│       ├── java/.../RestaurantPlugin.java  # 5 工具
│       └── resources/META-INF/       # plugin.yaml + SPI
└── docs/
    ├── 使用说明.md
    └── images/              # 验证截图 ×4
```
