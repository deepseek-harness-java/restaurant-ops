package cn.xiaofuge.r.app;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 餐厅经营 REST API */
@RestController
public class RController {

    private final RStore store;

    public RController(RStore store) { this.store = store; }

    @GetMapping("/api/tables")
    public Map<String, Object> tables() {
        return Map.of("code", 0, "data", store.tableList());
    }

    @GetMapping("/api/dishes")
    public Map<String, Object> dishes(@RequestParam(required = false) String category) {
        return Map.of("code", 0, "data", store.dishList(category));
    }

    @PostMapping("/api/order")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = body.get("items") instanceof List ? (List<Map<String, Object>>) body.get("items") : List.of();
        return Map.of("code", 0, "data", store.createOrder(str(body.get("tableId")), items, str(body.get("note"))));
    }

    @PostMapping("/api/order/checkout")
    public Map<String, Object> checkout(@RequestBody Map<String, Object> body) {
        return Map.of("code", 0, "data", store.checkout(str(body.get("orderId"))));
    }

    @GetMapping("/api/orders")
    public Map<String, Object> orders() {
        return Map.of("code", 0, "data", store.orders.values());
    }

    @GetMapping("/api/analysis")
    public Map<String, Object> analysis() {
        return Map.of("code", 0, "data", store.analysis());
    }

    private static String str(Object v) { return v == null ? "" : String.valueOf(v); }
    private static double dbl(Object v, double dft) {
        try { return v == null ? dft : Double.parseDouble(String.valueOf(v)); }
        catch (Exception e) { return dft; }
    }
}
