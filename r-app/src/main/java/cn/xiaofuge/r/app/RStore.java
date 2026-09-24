package cn.xiaofuge.r.app;

import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/** 餐厅经营内存数据层：桌台、菜品、订单、经营分析 */
@Component
public class RStore {

    public record Table(String id, String name, int seats, String zone,
                        String status, String orderId, int guests) {}

    public record Dish(String id, String name, String category, double price,
                       double cost, int soldToday, int stock, String tag) {}

    public record OrderItem(String dishId, String dishName, int qty, double price) {}

    public record Order(String id, String tableId, List<OrderItem> items,
                        double total, String status, String time, String note) {}

    public final Map<String, Table> tables = new ConcurrentHashMap<>();
    public final Map<String, Dish> dishes = new ConcurrentHashMap<>();
    public final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong orderGen = new AtomicLong(200);

    @PostConstruct
    public void init() {
        table(new Table("t01", "1 号桌", 4, "大厅", "就餐中", "o101", 3));
        table(new Table("t02", "2 号桌", 4, "大厅", "空闲", "", 0));
        table(new Table("t03", "3 号桌", 6, "大厅", "待清台", "o102", 0));
        table(new Table("t04", "包间 A", 10, "包间", "就餐中", "o103", 8));
        table(new Table("t05", "5 号桌", 2, "窗边", "空闲", "", 0));
        table(new Table("t06", "6 号桌", 4, "窗边", "预订", "", 0));
        table(new Table("t07", "7 号桌", 4, "大厅", "空闲", "", 0));
        table(new Table("t08", "包间 B", 12, "包间", "空闲", "", 0));

        dish(new Dish("d01", "招牌红烧肉", "热菜", 68, 24, 32, 40, "镇店"));
        dish(new Dish("d02", "清蒸鲈鱼", "热菜", 88, 38, 21, 15, "高毛利"));
        dish(new Dish("d03", "麻婆豆腐", "热菜", 28, 6, 45, 60, "走量"));
        dish(new Dish("d04", "蒜蓉西兰花", "热菜", 26, 7, 28, 35, ""));
        dish(new Dish("d05", "老鸭汤", "汤羹", 58, 22, 18, 12, "低库存"));
        dish(new Dish("d06", "酸梅汤", "饮品", 12, 2, 96, 0, "售罄"));
        dish(new Dish("d07", "桂花米酒", "饮品", 18, 4, 41, 50, ""));
        dish(new Dish("d08", "扬炒饭", "主食", 22, 5, 52, 80, "走量"));
        dish(new Dish("d09", "蟹粉小笼", "点心", 32, 11, 36, 25, ""));
        dish(new Dish("d10", "桂花糕", "点心", 16, 4, 24, 30, ""));

        order(new Order("o101", "t01", List.of(
                new OrderItem("d01", "招牌红烧肉", 1, 68),
                new OrderItem("d03", "麻婆豆腐", 1, 28),
                new OrderItem("d08", "扬炒饭", 1, 22)), 118, "就餐中", "11:52", "少辣"));
        order(new Order("o103", "t04", List.of(
                new OrderItem("d02", "清蒸鲈鱼", 2, 88),
                new OrderItem("d01", "招牌红烧肉", 2, 68),
                new OrderItem("d05", "老鸭汤", 1, 58),
                new OrderItem("d09", "蟹粉小笼", 2, 32)), 434, "就餐中", "12:10", "包间商务宴请"));
        order(new Order("o102", "t03", List.of(
                new OrderItem("d04", "蒜蓉西兰花", 1, 26),
                new OrderItem("d09", "蟹粉小笼", 1, 32)), 58, "已结账", "11:20", ""));
    }

    private void table(Table t) { tables.put(t.id(), t); }
    private void dish(Dish d) { dishes.put(d.id(), d); }
    private void order(Order o) { orders.put(o.id(), o); }

    /** 桌台列表 */
    public List<Table> tableList() {
        return tables.values().stream().sorted(Comparator.comparing(Table::id)).collect(Collectors.toList());
    }

    /** 菜品列表：可按分类 */
    public List<Dish> dishList(String category) {
        return dishes.values().stream()
                .filter(d -> category == null || category.isBlank() || d.category().equals(category))
                .sorted(Comparator.comparing(Dish::soldToday).reversed())
                .collect(Collectors.toList());
    }

    /** 点菜下单 */
    public Map<String, Object> createOrder(String tableId, List<Map<String, Object>> items, String note) {
        Table t = tables.get(tableId);
        if (t == null) return Map.of("ok", false, "message", "桌台不存在: " + tableId);
        if (!List.of("空闲", "预订").contains(t.status())) return Map.of("ok", false, "message", "桌台当前 " + t.status() + "，不能开台");
        if (items == null || items.isEmpty()) return Map.of("ok", false, "message", "至少点一道菜");
        List<OrderItem> list = new ArrayList<>();
        double total = 0;
        for (Map<String, Object> it : items) {
            Dish d = dishes.get(str(it.get("dishId")));
            if (d == null) return Map.of("ok", false, "message", "菜品不存在: " + it.get("dishId"));
            if (d.stock() < 1) return Map.of("ok", false, "message", "「" + d.name() + "」已售罄");
            int qty = Math.max(1, (int) dbl(it.get("qty"), 1));
            list.add(new OrderItem(d.id(), d.name(), qty, d.price()));
            total += d.price() * qty;
        }
        String id = "o" + orderGen.incrementAndGet();
        Order o = new Order(id, tableId, list, total, "就餐中", "12:30", note == null ? "" : note);
        orders.put(id, o);
        tables.put(tableId, new Table(t.id(), t.name(), t.seats(), t.zone(), "就餐中", id, t.seats() / 2));
        return Map.of("ok", true, "orderId", id, "total", total, "table", t.name());
    }

    /** 结账：清台 */
    public Map<String, Object> checkout(String orderId) {
        Order o = orders.get(orderId);
        if (o == null) return Map.of("ok", false, "message", "订单不存在");
        Order n = new Order(o.id(), o.tableId(), o.items(), o.total(), "已结账", o.time(), o.note());
        orders.put(orderId, n);
        Table t = tables.get(o.tableId());
        if (t != null) tables.put(t.id(), new Table(t.id(), t.name(), t.seats(), t.zone(), "待清台", orderId, 0));
        return Map.of("ok", true, "orderId", orderId, "total", o.total());
    }

    /** 翻台率与经营分析 */
    public Map<String, Object> analysis() {
        List<Order> all = new ArrayList<>(orders.values());
        double revenue = all.stream().filter(o -> !"已退".equals(o.status())).mapToDouble(Order::total).sum();
        long dining = tables.values().stream().filter(t -> "就餐中".equals(t.status())).count();
        long idle = tables.values().stream().filter(t -> "空闲".equals(t.status())).count();
        long dirty = tables.values().stream().filter(t -> "待清台".equals(t.status())).count();
        int seats = tables.values().stream().mapToInt(Table::seats).sum();
        double turnover = Math.round(all.size() * 10.0 / tables.size()) / 10.0;
        double grossMargin = revenue == 0 ? 0 : Math.round((revenue - costOf(all)) / revenue * 1000.0) / 10.0;

        List<Map<String, Object>> top = dishList(null).stream().limit(5).map(d -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", d.name()); m.put("sold", d.soldToday());
            m.put("revenue", Math.round(d.soldToday() * d.price()));
            m.put("marginRate", Math.round((d.price() - d.cost) / d.price * 1000.0) / 10.0);
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> warnings = dishes.values().stream()
                .filter(d -> d.stock() <= 15)
                .map(d -> Map.of("name", (Object) d.name(), "stock", d.stock(),
                        "suggest", d.stock() == 0 ? "已售罄，建议下架或补货" : "库存偏低，建议补货"))
                .collect(Collectors.toList());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", "2026-09-24 午市");
        m.put("revenue", revenue);
        m.put("orderCount", all.size());
        m.put("turnoverRate", turnover);
        m.put("grossMarginRate", grossMargin);
        m.put("seats", seats);
        m.put("tableStatus", Map.of("就餐中", dining, "空闲", idle, "待清台", dirty, "预订",
                tables.values().stream().filter(t -> "预订".equals(t.status())).count()));
        m.put("topDishes", top);
        m.put("stockWarnings", warnings);
        return m;
    }

    private double costOf(List<Order> os) {
        double c = 0;
        for (Order o : os) for (OrderItem i : o.items()) {
            Dish d = dishes.get(i.dishId());
            if (d != null) c += d.cost() * i.qty();
        }
        return c;
    }

    private static String str(Object v) { return v == null ? "" : String.valueOf(v); }
    private static double dbl(Object v, double dft) {
        try { return v == null ? dft : Double.parseDouble(String.valueOf(v)); }
        catch (Exception e) { return dft; }
    }
}
