import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 库存需求预测 + 智能补货建议 演示程序
 * <p>
 * 现状：系统只有 stockQuantity / minStock / 低库存预警，属于"低于阈值才报警"的被动响应。
 * 本类基于历史出入库流水，用时间序列预测未来 N 天库存走势，提前给出"X 天后会低于安全库存，建议补货 Y 个"的建议。
 * <p>
 * 预测算法对标简化版 Prophet：趋势项（最小二乘线性回归）+ 季节性项（周期均值法）+ 残差指数平滑。
 * 之所以不直接引入 Prophet / DL4J：
 *   1) 当前是 war 包部署的 Servlet 项目，引入 Python 进程或大体积深度学习库会显著增加部署复杂度；
 *   2) 库存流水通常为低频、强趋势、弱季节数据，线性回归 + 周期均值在工程上足够稳定可解释；
 *   3) 算法零外部依赖，后续可平滑替换为 Prophet/LSTM，调用入口（forecast 方法签名）保持不变。
 * <p>
 * 运行：直接右键 Run Test.main 即可看到对每个元件的预测与补货建议。
 */
public class Test {

    /** 预测未来天数 */
    private static final int FORECAST_DAYS = 14;
    /** 补货目标：补到 minStock 的多少倍（安全余量系数） */
    private static final double SAFETY_FACTOR = 1.5;
    /** 补货最小起订量，避免建议只补 1~2 个无意义的零碎单 */
    private static final int MIN_ORDER_QTY = 10;

    public static void main(String[] args) {
        System.out.println("================ 航空电子元件库存需求预测与补货建议 ================");
        System.out.println("预测窗口：未来 " + FORECAST_DAYS + " 天");
        System.out.println("安全余量系数：补货目标 = minStock * " + SAFETY_FACTOR);
        System.out.println();

        // 模拟从 inventory_records 表 + components 表查出的历史流水快照
        // key = 元件名（演示用，真实代码里用 componentId）
        Map<String, ComponentSnapshot> snapshots = MockInventoryData.load();

        for (Map.Entry<String, ComponentSnapshot> e : snapshots.entrySet()) {
            ComponentSnapshot snap = e.getValue();
            ForecastResult result = forecast(snap, FORECAST_DAYS);
            printReport(snap, result);
        }

        System.out.println("==================================================================");
        System.out.println("说明：本演示使用线性回归(趋势) + 周期均值(季节性) + 指数平滑(残差) 组合预测。");
        System.out.println("工程接入时：把 MockInventoryData.load() 换成 InventoryRecordDao 按日聚合查询，");
        System.out.println("         forecast() 方法签名无需改动。");
    }

    // ============================ 预测核心 ============================

    /**
     * 对单个元件做未来 N 天库存走势预测。
     *
     * @param snap         元件快照（当前库存、安全库存、按日聚合的净出库序列）
     * @param forecastDays 预测天数
     * @return 预测结果，含每日预测库存、跌破安全库存的天数、建议补货量
     */
    public static ForecastResult forecast(ComponentSnapshot snap, int forecastDays) {
        List<DailyPoint> history = snap.dailyNetOut; // 正值表示净出库，负值表示净入库
        int n = history.size();

        ForecastResult result = new ForecastResult();
        result.componentName = snap.name;
        result.currentStock = snap.currentStock;
        result.minStock = snap.minStock;
        result.forecastDays = forecastDays;

        // ---------- 1. 趋势项：对净出库做最小二乘线性回归 y = a + b * t ----------
        // 没有历史数据：退化为保守策略，按 0 净出库预测（即库存维持不变）
        double slope = 0.0;
        double intercept = 0.0;
        if (n >= 2) {
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            for (int t = 0; t < n; t++) {
                double y = history.get(t).value;
                sumX += t;
                sumY += y;
                sumXY += t * y;
                sumX2 += (double) t * t;
            }
            double denom = n * sumX2 - sumX * sumX;
            if (Math.abs(denom) > 1e-9) {
                slope = (n * sumXY - sumX * sumY) / denom;       // 每日净出库增量
                intercept = (sumY - slope * sumX) / n;            // 起始日净出库
            }
        }

        // ---------- 2. 季节性项：周期 = 7（周内出库规律），取每个相位的历史均值 ----------
        int period = 7;
        double[] seasonal = new double[period];
        int[] seasonalCount = new int[period];
        for (int t = 0; t < n; t++) {
            int phase = t % period;
            seasonal[phase] += history.get(t).value;
            seasonalCount[phase]++;
        }
        for (int p = 0; p < period; p++) {
            if (seasonalCount[p] > 0) {
                seasonal[p] /= seasonalCount[p];
            }
        }
        // 去掉季节项的均值，避免与趋势项重复计数（Prophet 的标准做法）
        double seasonalMean = 0;
        int nonzeroPhases = 0;
        for (int p = 0; p < period; p++) {
            if (seasonalCount[p] > 0) {
                seasonalMean += seasonal[p];
                nonzeroPhases++;
            }
        }
        if (nonzeroPhases > 0) {
            seasonalMean /= nonzeroPhases;
            for (int p = 0; p < period; p++) {
                seasonal[p] -= seasonalMean;
            }
        }

        // ---------- 3. 残差指数平滑：history - (trend + seasonal)，对残差做 ETS 平滑 ----------
        double alpha = 0.4; // 平滑系数，越大越看重近期
        double lastResidual = 0.0;
        if (n >= 1) {
            double[] residuals = new double[n];
            for (int t = 0; t < n; t++) {
                double trend = intercept + slope * t;
                double season = seasonal[t % period];
                residuals[t] = history.get(t).value - (trend + season);
            }
            lastResidual = residuals[n - 1];
            for (int t = 1; t < n; t++) {
                lastResidual = alpha * residuals[t] + (1 - alpha) * lastResidual;
            }
        }

        // ---------- 4. 用组合模型外推未来 forecastDays 天的净出库，累加得到库存走势 ----------
        int runningStock = snap.currentStock;
        int breachDay = -1;          // 第几天后跌破安全库存（-1 表示预测期内未跌破）
        int lowestStock = runningStock;
        int lowestDay = 0;

        for (int d = 1; d <= forecastDays; d++) {
            int t = n + d - 1;
            double trendNet = intercept + slope * t;
            double seasonNet = seasonal[t % period];
            double predictedNet = trendNet + seasonNet + lastResidual;
            // 净出库不可能为负库存单次冲击超过当前库存：做下限保护
            if (predictedNet > runningStock) {
                predictedNet = runningStock;
            }
            runningStock -= (int) Math.round(predictedNet);
            if (runningStock < 0) runningStock = 0;

            result.dailyStock.add(runningStock);
            result.dailyNetOut.add((int) Math.round(predictedNet));

            // 使用 <= 触发：到达安全库存即视为告急（比 SQL 的 < 更保守，留出缓冲）
            if (breachDay < 0 && runningStock <= snap.minStock) {
                breachDay = d;
            }
            if (runningStock < lowestStock) {
                lowestStock = runningStock;
                lowestDay = d;
            }
        }

        result.breachDay = breachDay;
        result.lowestStock = lowestStock;
        result.lowestDay = lowestDay;

        // ---------- 5. 智能补货建议 ----------
        // 策略：如果预测期内会跌破安全库存，则建议补货到 minStock * SAFETY_FACTOR；
        //       若当前已低于安全库存，立即补；否则建议在跌破前补。
        int targetStock = (int) Math.ceil(snap.minStock * SAFETY_FACTOR);
        if (snap.currentStock < snap.minStock) {
            // 已低于安全库存：立即补
            int orderQty = targetStock - snap.currentStock;
            result.needReorder = true;
            result.suggestOrderQty = Math.max(orderQty, MIN_ORDER_QTY);
            result.reorderDay = 0;
            result.reason = "当前库存已低于安全库存，需立即补货";
        } else if (breachDay > 0) {
            // 预测期内会跌破：在跌破前 1 天补（留 1 天采购提前期）
            int reorderDay = Math.max(breachDay - 1, 0);
            // 预测补货当天的库存
            int stockAtReorder = reorderDay == 0
                    ? snap.currentStock
                    : result.dailyStock.get(reorderDay - 1);
            int orderQty = targetStock - stockAtReorder;
            result.needReorder = true;
            result.suggestOrderQty = Math.max(orderQty, MIN_ORDER_QTY);
            result.reorderDay = reorderDay;
            result.reason = "预测 " + breachDay + " 天后将低于安全库存，建议提前补货";
        } else {
            result.needReorder = false;
            result.suggestOrderQty = 0;
            result.reorderDay = -1;
            result.reason = "预测期内库存充足，暂无需补货";
        }

        return result;
    }

    // ============================ 报告打印 ============================

    private static void printReport(ComponentSnapshot snap, ForecastResult r) {
        System.out.println("------------------------------------------------------------------");
        System.out.printf("元件: %-22s  当前库存: %4d  安全库存(minStock): %4d%n",
                snap.name, r.currentStock, r.minStock);
        System.out.println("未来 " + r.forecastDays + " 天预测库存走势（天: 净出库 -> 库存）:");
        LocalDate today = LocalDate.now();
        for (int d = 0; d < r.dailyStock.size(); d++) {
            LocalDate date = today.plus(d + 1, ChronoUnit.DAYS);
            String marker = r.dailyStock.get(d) < r.minStock ? "  ⚠低于安全库存" : "";
            System.out.printf("  +%-2d天 %-10s  净出库%+4d -> 库存%4d%s%n",
                    d + 1, date.toString(), r.dailyNetOut.get(d), r.dailyStock.get(d), marker);
        }
        System.out.println("  => 最低库存: " + r.lowestStock + " （第 " + r.lowestDay + " 天）");

        if (r.needReorder) {
            String when = r.reorderDay == 0 ? "立即" : ("第 " + r.reorderDay + " 天内");
            System.out.printf("  🔔 补货建议: %s补货 %d 个（目标库存 %d = minStock × %.1f）%n",
                    when, r.suggestOrderQty,
                    (int) Math.ceil(r.minStock * SAFETY_FACTOR), SAFETY_FACTOR);
            System.out.println("     原因: " + r.reason);
        } else {
            System.out.println("  ✅ " + r.reason);
        }
        System.out.println();
    }

    // ============================ 数据结构 ============================

    /** 元件快照 */
    static class ComponentSnapshot {
        String name;
        int currentStock;
        int minStock;
        /** 按日聚合的历史净出库序列（正=净出库，负=净入库），按时间升序 */
        List<DailyPoint> dailyNetOut = new ArrayList<>();
    }

    /** 单日数据点 */
    static class DailyPoint {
        LocalDate date;
        double value; // 净出库 = 出库 - 入库

        DailyPoint(LocalDate date, double value) {
            this.date = date;
            this.value = value;
        }
    }

    /** 预测结果 */
    static class ForecastResult {
        String componentName;
        int currentStock;
        int minStock;
        int forecastDays;
        List<Integer> dailyStock = new ArrayList<>();  // 每天预测库存
        List<Integer> dailyNetOut = new ArrayList<>(); // 每天预测净出库
        int breachDay;       // 跌破安全库存的天数，-1=未跌破
        int lowestStock;     // 预测期最低库存
        int lowestDay;       // 最低库存出现的天数
        boolean needReorder; // 是否需要补货
        int suggestOrderQty; // 建议补货数量
        int reorderDay;      // 建议补货时点（0=立即，-1=无需）
        String reason;       // 建议原因
    }

    // ============================ 模拟数据 ============================
    // 真实接入时替换为 InventoryRecordDao.search() + 按日聚合 SQL，
    // 例如：
    //   SELECT DATE(create_time) d,
    //          SUM(CASE WHEN type='out' THEN quantity ELSE 0 END) -
    //          SUM(CASE WHEN type='in'  THEN quantity ELSE 0 END) AS net_out
    //   FROM inventory_records WHERE component_id = ?
    //   GROUP BY DATE(create_time) ORDER BY d ASC;

    static class MockInventoryData {

        static Map<String, ComponentSnapshot> load() {
            Map<String, ComponentSnapshot> map = new LinkedHashMap<>();
            Random rnd = new Random(42); // 固定种子保证可复现

            // 1) 高精度气压传感器 AVS-001：当前库存 50，minStock=10，历史净出库缓慢上升
            //    数据来自 init.sql 真实流水：6 天里多次出库（5, 8）+ 一次入库 35
            map.put("AVS-001 高精度气压传感器",
                    build("AVS-001 高精度气压传感器", 72, 10,
                            new double[]{0, 5, 0, 0, 8, 0}, rnd));

            // 2) 圆形航空插头 AVC-001：消耗快、有周期性（每周二/四领用多）
            map.put("AVC-001 圆形航空插头",
                    build("AVC-001 圆形航空插头", 155, 50,
                            new double[]{10, 20, 0, 0, 15, 0}, rnd));

            // 3) 屏蔽双绞线 AVW-001：高频出库，即将跌破 100 安全库存
            map.put("AVW-001 屏蔽双绞线",
                    build("AVW-001 屏蔽双绞线", 495, 100,
                            new double[]{0, 15, 0, 0, 50, 10}, rnd));

            // 4) 发动机参数指示器 AVI-002：当前 6，minStock=2，已低于安全库存，需立即补
            map.put("AVI-002 发动机参数指示器",
                    build("AVI-002 发动机参数指示器", 6, 2,
                            new double[]{0, 0, 0, 2, 0, 0}, rnd));

            // 5) 28V直流电源模块 AVP-001：消耗稳定，库存充足，预测期内不会跌破
            map.put("AVP-001 28V直流电源模块",
                    build("AVP-001 28V直流电源模块", 25, 5,
                            new double[]{0, 0, 0, 0, 0, 0}, rnd));

            return map;
        }

        /**
         * 用一段初始历史净出库序列，外推生成 30 天的历史数据（加少量噪声），
         * 让预测模型有足够样本学到趋势和季节性。
         *
         * @param name         元件名
         * @param currentStock 当前库存
         * @param minStock     安全库存
         * @param seedPattern  初始 6 天的真实净出库（来自 init.sql）
         */
        private static ComponentSnapshot build(String name, int currentStock, int minStock,
                                               double[] seedPattern, Random rnd) {
            ComponentSnapshot snap = new ComponentSnapshot();
            snap.name = name;
            snap.currentStock = currentStock;
            snap.minStock = minStock;

            // 历史窗口：用 seedPattern 重复 5 轮 ≈ 30 天，并加 ±20% 噪声
            LocalDate today = LocalDate.now();
            int histDays = 30;
            for (int t = 0; t < histDays; t++) {
                LocalDate d = today.minus(histDays - t, ChronoUnit.DAYS);
                double base = seedPattern[t % seedPattern.length];
                double noise = base * (rnd.nextDouble() * 0.4 - 0.2); // ±20%
                double v = Math.max(0, base + noise);
                snap.dailyNetOut.add(new DailyPoint(d, v));
            }
            // 按日期排序，保证升序
            snap.dailyNetOut.sort(Comparator.comparing(p -> p.date));
            return snap;
        }
    }
}
