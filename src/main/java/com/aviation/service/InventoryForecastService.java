package com.aviation.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库存需求预测 + 智能补货建议 —— Java 侧接口对接层。
 * <p>
 * 本类 <b>不实现任何机器学习算法</b>。预测算法（Prophet / LSTM / 时序回归等）全部由
 * Python 服务完成，Java 仅负责：
 *   1) 把元件快照（当前库存、安全库存、历史出入库序列）打包成 JSON；
 *   2) 通过 HTTP POST 调用 Python 预测服务；
 *   3) 解析 Python 返回的 JSON，转成 Java 业务对象给上层 Servlet 使用。
 * <p>
 * 与 Python 服务的契约（建议双方共同维护）：
 * <pre>
 * 请求 POST {PYTHON_BASE_URL}/forecast
 * Content-Type: application/json
 * Body:
 * {
 *   "componentId": 1,
 *   "componentName": "高精度气压传感器",
 *   "currentStock": 72,
 *   "minStock": 10,
 *   "forecastDays": 14,
 *   "history": [                        // 按日聚合的历史净出库（正=出库，负=入库），时间升序
 *     {"date": "2026-06-22", "value": 0.0},
 *     {"date": "2026-06-23", "value": 5.2},
 *     ...
 *   ]
 * }
 *
 * 响应 Body:
 * {
 *   "code": 200,
 *   "data": {
 *     "componentName": "高精度气压传感器",
 *     "currentStock": 72,
 *     "minStock": 10,
 *     "forecastDays": 14,
 *     "dailyStock":   [70, 67, 63, ...],   // 未来每天预测库存
 *     "dailyNetOut":  [2, 3, 4, ...],      // 未来每天预测净出库
 *     "breachDay": 12,                     // 第几天跌破安全库存，-1=未跌破
 *     "lowestStock": 2, "lowestDay": 12,
 *     "needReorder": true,
 *     "suggestOrderQty": 10,
 *     "reorderDay": 11,                    // 建议补货时点（0=立即，-1=无需）
 *     "reason": "预测 12 天后将低于安全库存，建议提前补货"
 *   }
 * }
 * </pre>
 * <p>
 * 配置：Python 服务地址通过环境变量 {@code PYTHON_ML_URL} 注入，默认 {@code http://localhost:5000}。
 */
public class InventoryForecastService {

    /** Python 机器学习服务基址，可通过环境变量 PYTHON_ML_URL 覆盖 */
    private static final String PYTHON_BASE_URL =
            System.getenv().getOrDefault("PYTHON_ML_URL", "http://localhost:5000");

    /** HTTP 超时（秒）。预测是相对耗时的操作，留足时间 */
    private static final int TIMEOUT_SECONDS = 30;

    /** 单例 HttpClient（JDK 11+ 内置，无需任何外部依赖） */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ============================ 对外接口 ============================

    /**
     * 调用 Python 服务做库存预测。
     *
     * @param snap         元件快照（当前库存、安全库存、历史净出库序列）
     * @param forecastDays 预测天数
     * @return 预测结果；Python 服务不可达时返回降级结果（needReorder=false + reason 提示）
     */
    public static ForecastResult forecast(ComponentSnapshot snap, int forecastDays) {
        Map<String, Object> request = new HashMap<>();
        request.put("componentId", snap.componentId);
        request.put("componentName", snap.name);
        request.put("currentStock", snap.currentStock);
        request.put("minStock", snap.minStock);
        request.put("forecastDays", forecastDays);
        request.put("history", snap.history); // List<Map<String,Object>>，见 toHistoryMap

        try {
            String body = MAPPER.writeValueAsString(request);
            String resp = postJson(PYTHON_BASE_URL + "/forecast", body);

            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = MAPPER.readValue(resp, Map.class);
            // 兼容两种返回格式：{code,data} 包装 或 直接返回 data 字段
            Map<String, Object> data = respMap.containsKey("data")
                    ? (Map<String, Object>) respMap.get("data")
                    : respMap;

            return parseForecastResult(data);
        } catch (Exception e) {
            // 降级：Python 服务不可达时，不阻塞业务，返回"暂不可用"
            return degradedResult(snap, forecastDays,
                    "预测服务暂不可用：" + e.getMessage());
        }
    }

    // ============================ HTTP 工具 ============================

    private static String postJson(String url, String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("Python 服务返回 HTTP " + resp.statusCode()
                    + ": " + resp.body());
        }
        return resp.body();
    }

    // ============================ 响应解析 ============================

    @SuppressWarnings("unchecked")
    private static ForecastResult parseForecastResult(Map<String, Object> data) {
        ForecastResult r = new ForecastResult();
        r.componentName = (String) data.get("componentName");
        r.currentStock = toInt(data.get("currentStock"));
        r.minStock = toInt(data.get("minStock"));
        r.forecastDays = toInt(data.get("forecastDays"));
        r.dailyStock = toIntList((List<Object>) data.get("dailyStock"));
        r.dailyNetOut = toIntList((List<Object>) data.get("dailyNetOut"));
        r.breachDay = toInt(data.get("breachDay"));
        r.lowestStock = toInt(data.get("lowestStock"));
        r.lowestDay = toInt(data.get("lowestDay"));
        r.needReorder = Boolean.TRUE.equals(data.get("needReorder"));
        r.suggestOrderQty = toInt(data.get("suggestOrderQty"));
        r.reorderDay = toInt(data.get("reorderDay"));
        r.reason = (String) data.get("reason");
        return r;
    }

    private static ForecastResult degradedResult(ComponentSnapshot snap, int forecastDays, String reason) {
        ForecastResult r = new ForecastResult();
        r.componentName = snap.name;
        r.currentStock = snap.currentStock;
        r.minStock = snap.minStock;
        r.forecastDays = forecastDays;
        r.dailyStock = Collections.emptyList();
        r.dailyNetOut = Collections.emptyList();
        r.breachDay = -1;
        r.needReorder = false;
        r.suggestOrderQty = 0;
        r.reorderDay = -1;
        r.reason = reason;
        return r;
    }

    // ============================ 类型转换工具 ============================

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        return Integer.parseInt(o.toString());
    }

    private static List<Integer> toIntList(List<Object> list) {
        if (list == null) return Collections.emptyList();
        List<Integer> result = new java.util.ArrayList<>(list.size());
        for (Object o : list) result.add(toInt(o));
        return result;
    }

    // ============================ 数据结构（与 Python 交互的 DTO） ============================

    /** 元件快照 —— Java 侧组装，序列化成 JSON 发给 Python */
    public static class ComponentSnapshot {
        public Integer componentId;
        public String name;
        public int currentStock;
        public int minStock;
        /** 按日聚合的历史净出库序列（正=净出库，负=净入库），按时间升序 */
        public List<Map<String, Object>> history = new java.util.ArrayList<>();
    }

    /** 预测结果 —— Python 返回 JSON 反序列化得到 */
    public static class ForecastResult {
        public String componentName;
        public int currentStock;
        public int minStock;
        public int forecastDays;
        public List<Integer> dailyStock;   // 每天预测库存
        public List<Integer> dailyNetOut;  // 每天预测净出库
        public int breachDay;       // 跌破安全库存的天数，-1=未跌破
        public int lowestStock;     // 预测期最低库存
        public int lowestDay;       // 最低库存出现的天数
        public boolean needReorder; // 是否需要补货
        public int suggestOrderQty; // 建议补货数量
        public int reorderDay;      // 建议补货时点（0=立即，-1=无需）
        public String reason;       // 建议原因
    }
}
