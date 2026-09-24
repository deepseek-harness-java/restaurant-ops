package cn.xiaofuge.r.plugin;

import cn.xiaofuge.deepseek.harness.domain.model.entity.AbstractTool;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolDefinition;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolExecutionResult;
import cn.xiaofuge.deepseek.harness.domain.model.entity.ToolRunContext;
import cn.xiaofuge.deepseek.harness.domain.spi.AbstractHarnessPlugin;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginContext;
import cn.xiaofuge.deepseek.harness.domain.spi.PluginHookResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** AI 店长助手插件：把 restaurant-ops REST API 注册为 DSH Agent 工具 */
public class RestaurantPlugin extends AbstractHarnessPlugin {

    public static final String PLUGIN_ID = "restaurant-copilot";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    public RestaurantPlugin() { super(PLUGIN_ID); }

    @Override
    public List<ToolDefinition> tools() {
        return List.of(
                new TableBoardTool(),
                new MenuTool(),
                new OrderTool(),
                new CheckoutTool(),
                new AnalysisTool());
    }

    @Override
    public void configure(PluginContext context) {
        super.configure(context);
        context.registerSystemPrompt("restaurant-capabilities", 20, """
                ## AI 店长助手（餐厅经营 · 2026-09-24 午市）
                - 用户问"还有没有位子/桌台情况/包间" → table_board
                - 用户问"有什么菜/推荐菜品/XX 多少钱/毛利" → menu（可按 category：热菜/汤羹/饮品/主食/点心）
                - 用户说"开台点菜/帮客人下单" → order_create（tableId + items[{dishId,qty}]，先查桌台空闲、菜品库存；报出合计）
                - 用户说"结账/清台" → checkout（orderId）
                - 用户问"今天生意怎么样/流水/翻台/毛利/哪个菜好卖/要不要补货" → analysis
                - 回答要求：
                  1) 经营分析用数字说话：流水、翻台率、毛利率、Top 菜品销量
                  2) 建议可执行：库存预警给补货建议、滞销给下架或促销建议
                  3) 开台前必须确认桌台状态；点菜前确认菜品有货
                  4) 数据来自工具返回，禁止编造
                """);
        context.registerHook("PRE_TOOL_USE", (toolName, payloadJson) -> {
            if (toolName != null && toolName.startsWith("plugin__" + PLUGIN_ID + "__")) {
                return PluginHookResult.context("audit: restaurant tool call.");
            }
            return null;
        });
    }

    private String get(String path, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path)).GET().build());
    }

    private String post(String path, String jsonBody, Map<String, Object> args) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl(args) + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8)).build());
    }

    private String baseUrl(Map<String, Object> args) {
        Object override = args == null ? null : args.get("appBaseUrl");
        return override == null || String.valueOf(override).isBlank()
                ? System.getenv().getOrDefault("RESTAURANT_APP_BASE_URL", "http://127.0.0.1:18092")
                : String.valueOf(override);
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return "{\"error\":true,\"status\":" + resp.statusCode() + "}";
            return resp.body();
        } catch (Exception e) {
            return "{\"error\":true,\"message\":\"" + String.valueOf(e.getMessage()).replace("\"", "'") + "\"}";
        }
    }

    private String str(Map<String, Object> args, String key) {
        Object v = args == null ? null : args.get(key);
        return v == null ? "" : String.valueOf(v);
    }

    private String json(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private class TableBoardTool extends AbstractTool {
        @Override public String name() { return "table_board"; }
        @Override public String description() {
            return "桌台看板：每桌的座位数/区域/状态（空闲、就餐中、待清台、预订）/当前订单。"
                    + "何时必须调用：查空位、安排包间、开台前确认桌台状态。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/tables", args));
        }
    }

    private class MenuTool extends AbstractTool {
        @Override public String name() { return "menu"; }
        @Override public String description() {
            return "菜品列表：价格/成本/今日销量/库存/标签（镇店、走量、高毛利），可按分类过滤。"
                    + "何时必须调用：查菜价、推荐菜、找 dishId、看毛利与库存。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema().prop("category", stringSchema("可选：热菜 / 汤羹 / 饮品 / 主食 / 点心")).build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String c = str(args, "category");
            return ok(get("/api/dishes" + (c.isBlank() ? "" : "?category=" + c), args));
        }
    }

    private class OrderTool extends AbstractTool {
        @Override public String name() { return "order_create"; }
        @Override public String description() {
            return "开台点菜：tableId + items 数组[{dishId, qty}] + note 备注。开台前先 table_board 确认空闲，点菜前确认库存。"
                    + "何时必须调用：客人入座点菜。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema()
                    .prop("tableId", stringSchema("桌台 ID，如 t02"))
                    .prop("items", stringSchema("菜品 JSON 数组字符串，如 [{\"dishId\":\"d01\",\"qty\":1}]"))
                    .prop("note", stringSchema("备注，如 少辣"))
                    .required("tableId", "items")
                    .build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return false; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            String items = str(args, "items");
            String body = "{\"tableId\":\"" + json(str(args, "tableId"))
                    + "\",\"items\":" + items
                    + ",\"note\":\"" + json(str(args, "note")) + "\"}";
            return ok(post("/api/order", body, args));
        }
    }

    private class CheckoutTool extends AbstractTool {
        @Override public String name() { return "checkout"; }
        @Override public String description() {
            return "结账清台：orderId 必填。结账后桌台变为待清台。"
                    + "何时必须调用：客人买单离店。";
        }
        @Override public Map<String, Object> parameters() {
            return objectSchema().prop("orderId", stringSchema("订单 ID，如 o101")).required("orderId").build();
        }
        @Override public boolean isConcurrencySafe(Object args) { return false; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(post("/api/order/checkout", "{\"orderId\":\"" + json(str(args, "orderId")) + "\"}", args));
        }
    }

    private class AnalysisTool extends AbstractTool {
        @Override public String name() { return "analysis"; }
        @Override public String description() {
            return "经营分析：流水/翻台率/毛利率/桌台状态分布/Top5 菜品销量与毛利/库存预警清单。"
                    + "何时必须调用：生意怎么样、哪个菜好卖、要不要补货、经营建议。";
        }
        @Override public Map<String, Object> parameters() { return objectSchema().build(); }
        @Override public boolean isConcurrencySafe(Object args) { return true; }
        @Override protected CompletableFuture<ToolExecutionResult> run(Map<String, Object> args, ToolRunContext ctx) {
            return ok(get("/api/analysis", args));
        }
    }
}
