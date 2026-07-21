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
 * 元件分类智能识别 —— Java 侧接口对接层。
 * <p>
 * 本类 <b>不实现任何机器学习算法</b>。TF-IDF / 朴素贝叶斯 / SVM 等模型训练与推理
 * 全部由 Python 服务（scikit-learn）完成，Java 仅负责：
 *   1) 把元件"名称 + 规格 + 描述"打包成 JSON；
 *   2) 通过 HTTP POST 调用 Python 分类服务；
 *   3) 解析 Python 返回的 JSON（推荐分类 + 置信度 + top-N 候选），转成 Java 业务对象。
 * <p>
 * 与 Python 服务的契约（建议双方共同维护）：
 * <pre>
 * 请求 POST {PYTHON_BASE_URL}/predictCategory
 * Content-Type: application/json
 * Body: {"name": "高频振动传感器", "spec": "VIB-900, 0~5kHz", "description": "发动机振动监测"}
 *
 * 响应 Body:
 * {
 *   "code": 200,
 *   "data": {
 *     "categoryId": 1,
 *     "categoryName": "航空传感器",
 *     "confidence": 0.985,
 *     "candidates": [                  // top-N 候选，按置信度降序
 *       {"categoryId": 1, "categoryName": "航空传感器", "confidence": 0.985},
 *       {"categoryId": 5, "categoryName": "航空仪表",   "confidence": 0.010},
 *       {"categoryId": 4, "categoryName": "航空线缆",   "confidence": 0.003}
 *     ]
 *   }
 * }
 * </pre>
 * <p>
 * 配置：Python 服务地址通过环境变量 {@code PYTHON_ML_URL} 注入，默认 {@code http://localhost:5000}。
 */
public class CategoryClassifierService {

    private static final String PYTHON_BASE_URL =
            System.getenv().getOrDefault("PYTHON_ML_URL", "http://localhost:5000");

    private static final int TIMEOUT_SECONDS = 10;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ============================ 对外接口 ============================

    /**
     * 调用 Python 服务做元件分类推荐。
     *
     * @param name        元件名称
     * @param spec        规格型号
     * @param description 备注描述
     * @return 分类推荐结果；Python 服务不可达时返回降级结果（categoryId=null + reason 提示）
     */
    public static Prediction predict(String name, String spec, String description) {
        Map<String, Object> request = new HashMap<>();
        request.put("name", name == null ? "" : name);
        request.put("spec", spec == null ? "" : spec);
        request.put("description", description == null ? "" : description);

        try {
            String body = MAPPER.writeValueAsString(request);
            String resp = postJson(PYTHON_BASE_URL + "/predictCategory", body);

            @SuppressWarnings("unchecked")
            Map<String, Object> respMap = MAPPER.readValue(resp, Map.class);
            Map<String, Object> data = respMap.containsKey("data")
                    ? (Map<String, Object>) respMap.get("data")
                    : respMap;

            return parsePrediction(data);
        } catch (Exception e) {
            return degradedPrediction("分类服务暂不可用：" + e.getMessage());
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
    private static Prediction parsePrediction(Map<String, Object> data) {
        Prediction p = new Prediction();
        p.categoryId = toIntOrNull(data.get("categoryId"));
        p.categoryName = (String) data.get("categoryName");
        p.confidence = toDouble(data.get("confidence"));
        p.reason = (String) data.get("reason");

        // 解析 top-N 候选
        List<Map<String, Object>> rawCandidates = (List<Map<String, Object>>) data.get("candidates");
        if (rawCandidates != null) {
            for (Map<String, Object> c : rawCandidates) {
                Prediction cand = new Prediction();
                cand.categoryId = toIntOrNull(c.get("categoryId"));
                cand.categoryName = (String) c.get("categoryName");
                cand.confidence = toDouble(c.get("confidence"));
                p.candidates.add(cand);
            }
        }
        return p;
    }

    private static Prediction degradedPrediction(String reason) {
        Prediction p = new Prediction();
        p.reason = reason;
        return p;
    }

    // ============================ 类型转换工具 ============================

    private static Integer toIntOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(o.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    // ============================ 数据结构（与 Python 交互的 DTO） ============================

    /** 分类推荐结果 —— Python 返回 JSON 反序列化得到 */
    public static class Prediction {
        /** 推荐分类 id，服务不可用时为 null */
        public Integer categoryId;
        /** 推荐分类名 */
        public String categoryName;
        /** 置信度 0~1 */
        public double confidence;
        /** top-N 候选列表，按置信度降序，前端可做"建议但可修改" */
        public List<Prediction> candidates = new java.util.ArrayList<>();
        /** 失败原因或额外说明 */
        public String reason;
    }
}
