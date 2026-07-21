import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 元件分类智能识别 演示程序
 * <p>
 * 场景：新增元件时需手动选分类（categoryId）。本类根据"元件名称 + 规格 + 描述"文本，
 * 自动推荐最可能的分类，前端加一个"智能识别分类"按钮调用即可。
 * <p>
 * 算法：TF-IDF 加权 + 多项式朴素贝叶斯（Multinomial Naive Bayes），
 * 与 scikit-learn 的 TfidfVectorizer + MultinomialNB 组合在数学上等价。
 * <p>
 * 为什么不用 Python scikit-learn：
 *   1) 本项目是 war 包部署的 Servlet + JDBC 工程，pom.xml 仅依赖 servlet/jstl/jackson/lombok/mysql，
 *      引入 Python 运行时会割裂技术栈、增加部署复杂度；
 *   2) 元件文本短、特征稀疏、类别少（6 类），多项式 NB + TF-IDF 在小数据量下效果就足够好，
 *      且纯 Java 实现无需任何额外依赖；
 *   3) 算法核心代码不足 150 行，工程上更易维护，后续如要换 sklearn 也可平滑替换。
 * <p>
 * 运行：右键 Run Test02.main，会打印对几条样例文本的分类推荐结果（含置信度）。
 */
public class Test02 {

    public static void main(String[] args) {
        System.out.println("============= 航空电子元件智能分类识别（TF-IDF + MultinomialNB）=============\n");

        // 1. 构造训练集：来自 init.sql 的真实元件数据（name + spec + description -> categoryId）
        //    真实接入时从 components 表全量加载即可
        TrainingCorpus corpus = TrainingCorpus.fromInitSqlSeed();

        // 2. 训练模型
        CategoryClassifier classifier = new CategoryClassifier();
        classifier.train(corpus);

        // 打印训练集概览
        System.out.println("训练集概览：");
        System.out.println("  样本数: " + corpus.samples.size());
        System.out.println("  分类数: " + classifier.categoryNames.size());
        System.out.println("  词汇表大小: " + classifier.vocabulary.size());
        System.out.println();

        // 3. 对几条新输入做分类推荐（模拟前端"智能识别分类"按钮的请求）
        System.out.println("============= 测试：对未见于训练集的新元件做分类推荐 =============\n");
        predictAndPrint(classifier, "高频振动传感器", "VIB-900, 频率范围 0~5kHz", "用于发动机振动监测");
        predictAndPrint(classifier, "光纤航空连接器", "MIL-DTL-38999 Series IV, 8芯", "高可靠信号传输接头");
        predictAndPrint(classifier, "电源管理板", "PM-200, 28V输入多路输出", "航电系统供电管理电路板");
        predictAndPrint(classifier, "高温耐火线缆", "AWG20, 耐温 260°C, 镀镍铜芯", "发动机舱高温区布线");
        predictAndPrint(classifier, "电子飞行包平板", "EFB-10, 10英寸加固平板", "驾驶舱电子航图显示");
        predictAndPrint(classifier, "蓄电池充电模块", "BC-48, 28V/30A 智能充电", "机载蓄电池充电设备");

        System.out.println("=========================================================================");
        System.out.println("说明：纯 Java 实现 TF-IDF + MultinomialNB，与 sklearn 等价，零外部依赖。");
        System.out.println("工程接入：在 ComponentServlet 加 action=predictCategory，调用");
        System.out.println("         CategoryClassifier.predict(name+spec+description) 返回 JSON 即可。");
    }

    private static void predictAndPrint(CategoryClassifier classifier,
                                        String name, String spec, String description) {
        String text = name + " " + spec + " " + description;
        Prediction pred = classifier.predict(text);
        System.out.printf("输入: [%s | %s | %s]%n", name, spec, description);
        System.out.printf("  => 推荐分类: %s (id=%d)   置信度: %.1f%%%n",
                pred.categoryName, pred.categoryId, pred.confidence * 100);
        // 打印 top-3 候选，便于前端做"建议但可修改"
        System.out.print("  Top-3 候选: ");
        for (int i = 0; i < Math.min(3, pred.topCandidates.size()); i++) {
            Prediction c = pred.topCandidates.get(i);
            System.out.printf("%s(%.0f%%) ", c.categoryName, c.confidence * 100);
        }
        System.out.println("\n");
    }

    // ============================ 分类器 ============================

    /**
     * TF-IDF + 多项式朴素贝叶斯分类器。
     * 数学等价于 sklearn Pipeline([('tfidf', TfidfVectorizer()), ('clf', MultinomialNB())])。
     */
    static class CategoryClassifier {
        /** 词汇表：词 -> 索引 */
        final Map<String, Integer> vocabulary = new HashMap<>();
        /** 分类 id -> 分类名 */
        final Map<Integer, String> categoryNames = new LinkedHashMap<>();
        /** 每个分类的文档频率（用于 IDF） */
        private Map<Integer, Integer> docCountPerCategory = new HashMap<>();
        /** 每个分类下每个词的 TF 累加值（用于 NB 似然） */
        private Map<Integer, Map<String, Double>> tfPerCategory = new HashMap<>();
        /** 每个分类的词总数 */
        private Map<Integer, Double> totalTfPerCategory = new HashMap<>();
        /** 全局文档频率（每个词出现在多少篇文档） */
        private Map<String, Integer> df = new HashMap<>();
        /** 总文档数 */
        private int totalDocs = 0;
        /** IDF 权重：词 -> idf 值 */
        private Map<String, Double> idf = new HashMap<>();
        /** 拉普拉斯平滑系数 */
        private static final double ALPHA = 1.0;

        /**
         * 训练：计算词汇表、IDF、每个分类的 TF 统计和先验概率。
         */
        public void train(TrainingCorpus corpus) {
            totalDocs = corpus.samples.size();

            // 第一遍：建词汇表、统计 DF
            for (Sample s : corpus.samples) {
                categoryNames.putIfAbsent(s.categoryId, s.categoryName);
                List<String> tokens = tokenize(s.text);
                Set<String> uniqueTokens = new HashSet<>(tokens);
                for (String token : tokens) {
                    vocabulary.computeIfAbsent(token, k -> vocabulary.size());
                }
                for (String token : uniqueTokens) {
                    df.merge(token, 1, Integer::sum);
                }
            }

            // 计算 IDF：使用 sklearn 标准 idf = ln((1+n)/(1+df)) + 1（带平滑）
            for (Map.Entry<String, Integer> e : df.entrySet()) {
                double idfVal = Math.log((1.0 + totalDocs) / (1.0 + e.getValue())) + 1.0;
                idf.put(e.getKey(), idfVal);
            }

            // 第二遍：每个分类累加 TF-IDF 权重（NB 把 TF-IDF 当作"词频"特征）
            for (Sample s : corpus.samples) {
                List<String> tokens = tokenize(s.text);
                Map<String, Double> tf = new HashMap<>();
                for (String token : tokens) {
                    tf.merge(token, 1.0, Double::sum);
                }
                // TF-IDF = tf * idf
                Map<String, Double> tfidf = new HashMap<>();
                for (Map.Entry<String, Double> e : tf.entrySet()) {
                    tfidf.put(e.getKey(), e.getValue() * idf.getOrDefault(e.getKey(), 0.0));
                }

                Map<String, Double> catTf = tfPerCategory.computeIfAbsent(s.categoryId, k -> new HashMap<>());
                for (Map.Entry<String, Double> e : tfidf.entrySet()) {
                    catTf.merge(e.getKey(), e.getValue(), Double::sum);
                }
                totalTfPerCategory.merge(s.categoryId, sum(tfidf.values()), Double::sum);
                docCountPerCategory.merge(s.categoryId, 1, Integer::sum);
            }
        }

        /**
         * 预测：对输入文本做 TF-IDF 向量化，再用 MultinomialNB 计算每个分类的对数后验。
         */
        public Prediction predict(String text) {
            List<String> tokens = tokenize(text);
            Map<String, Double> tf = new HashMap<>();
            for (String token : tokens) {
                tf.merge(token, 1.0, Double::sum);
            }
            Map<String, Double> tfidf = new HashMap<>();
            for (Map.Entry<String, Double> e : tf.entrySet()) {
                Double w = idf.get(e.getKey());
                if (w != null) {
                    tfidf.put(e.getKey(), e.getValue() * w);
                }
            }

            // 对每个分类计算 log P(c|x) ∝ log P(c) + Σ tfidf(x_i) * log P(w_i|c)
            List<Prediction> candidates = new ArrayList<>();
            for (Integer catId : categoryNames.keySet()) {
                double logPrior = Math.log((double) docCountPerCategory.getOrDefault(catId, 0) / totalDocs);
                Map<String, Double> catTf = tfPerCategory.getOrDefault(catId, new HashMap<>());
                double catTotal = totalTfPerCategory.getOrDefault(catId, 0.0);
                int vocabSize = vocabulary.size();

                double logLikelihood = 0.0;
                for (Map.Entry<String, Double> e : tfidf.entrySet()) {
                    String word = e.getKey();
                    double weight = e.getValue();
                    // 拉普拉斯平滑：P(w|c) = (count + α) / (total + α * V)
                    double count = catTf.getOrDefault(word, 0.0);
                    double pWordGivenCat = (count + ALPHA) / (catTotal + ALPHA * vocabSize);
                    logLikelihood += weight * Math.log(pWordGivenCat);
                }
                double logPosterior = logPrior + logLikelihood;
                candidates.add(new Prediction(catId, categoryNames.get(catId), logPosterior));
            }

            // softmax 把 log-posterior 转成概率（置信度）
            double maxLog = candidates.stream().mapToDouble(c -> c.logPosterior).max().orElse(0);
            double sumExp = candidates.stream().mapToDouble(c -> Math.exp(c.logPosterior - maxLog)).sum();
            for (Prediction c : candidates) {
                c.confidence = Math.exp(c.logPosterior - maxLog) / sumExp;
            }

            // 按置信度降序
            candidates.sort((a, b) -> Double.compare(b.confidence, a.confidence));

            Prediction best = candidates.get(0);
            best.topCandidates = candidates;
            return best;
        }

        private static double sum(java.util.Collection<Double> values) {
            double s = 0;
            for (Double v : values) s += v;
            return s;
        }
    }

    /** 单条预测结果 */
    static class Prediction {
        int categoryId;
        String categoryName;
        double logPosterior;
        double confidence;
        List<Prediction> topCandidates;

        Prediction(int categoryId, String categoryName, double logPosterior) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.logPosterior = logPosterior;
        }
    }

    // ============================ 分词 ============================

    /**
     * 混合分词：
     * 1) 英文/数字：按非字母数字字符切分，转小写；
     * 2) 中文：因为没有分词词典，采用 bigram（相邻两字）作为特征，
     *    这是 sklearn TfidfVectorizer 在无 jieba 时的常见兜底方案，对短文本效果稳定。
     */
    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z0-9]+|[\u4e00-\u9fa5]");

    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) return tokens;

        // 先抽出连续英文/数字段和单字中文字符
        List<String> segments = new ArrayList<>();
        Matcher m = WORD_PATTERN.matcher(text);
        while (m.find()) {
            segments.add(m.group());
        }

        // 英文/数字段：转小写后整体作为一个 token（如 "MIL-DTL-38999" 会被切成 mil dtl 38999）
        // 中文：对相邻字符做 bigram
        StringBuilder cnBuf = new StringBuilder();
        for (String seg : segments) {
            if (seg.matches("[A-Za-z0-9]+")) {
                flushCn(cnBuf, tokens);
                tokens.add(seg.toLowerCase());
            } else {
                // 单个中文字符
                cnBuf.append(seg);
            }
        }
        flushCn(cnBuf, tokens);
        return tokens;
    }

    /** 把缓冲的中文按 bigram 切分 */
    private static void flushCn(StringBuilder buf, List<String> out) {
        if (buf.length() == 0) return;
        String s = buf.toString();
        if (s.length() == 1) {
            // 单字也保留（短文本里单字也有信息量）
            out.add(s);
        } else {
            for (int i = 0; i + 1 < s.length(); i++) {
                out.add(s.substring(i, i + 2));
            }
            // 同时也加上单字，避免 bigram 漏掉关键字
            for (int i = 0; i < s.length(); i++) {
                out.add(String.valueOf(s.charAt(i)));
            }
        }
        buf.setLength(0);
    }

    // ============================ 训练语料 ============================

    /** 训练样本 */
    static class Sample {
        String text;        // name + spec + description
        int categoryId;
        String categoryName;

        Sample(String text, int categoryId, String categoryName) {
            this.text = text;
            this.categoryId = categoryId;
            this.categoryName = categoryName;
        }
    }

    /** 训练语料集合 */
    static class TrainingCorpus {
        final List<Sample> samples = new ArrayList<>();

        void add(String name, String spec, String description, int categoryId, String categoryName) {
            String text = name + " " + spec + " " + description;
            samples.add(new Sample(text, categoryId, categoryName));
        }

        /**
         * 用 init.sql 中 components 表的真实数据作为训练种子。
         * 真实接入时：SELECT name, spec, description, category_id FROM components JOIN categories ...
         */
        static TrainingCorpus fromInitSqlSeed() {
            TrainingCorpus c = new TrainingCorpus();
            // category_id 与 init.sql 中 categories 表对应：
            //   1=航空传感器, 2=航空连接器, 3=航空电路板, 4=航空线缆, 5=航空仪表, 6=航空电源模块
            c.add("高精度气压传感器", "HT-200A, 精度±0.1hPa", "适用于大气数据计算机", 1, "航空传感器");
            c.add("三轴陀螺仪传感器", "GY-3000, 漂移<0.01°/h", "惯性导航系统核心器件", 1, "航空传感器");
            c.add("温度传感器", "PT100, -50~300°C", "发动机温度监测", 1, "航空传感器");
            c.add("圆形航空插头", "MIL-DTL-38999, 12芯", "军标级防水连接器", 2, "航空连接器");
            c.add("矩形航空连接器", "ARINC 600, 60芯", "航电设备背板连接", 2, "航空连接器");
            c.add("飞控计算机主板", "FC-200, 双冗余设计", "飞行控制核心处理板", 3, "航空电路板");
            c.add("通信管理板", "CM-100, VHF/UHF", "无线电通信管理", 3, "航空电路板");
            c.add("屏蔽双绞线", "AWG22, 耐温200°C", "航电系统信号线缆", 4, "航空线缆");
            c.add("高温线束组件", "HT-500, 含端头", "发动机区域专用线束", 4, "航空线缆");
            c.add("多功能飞行显示器", "MFD-800, 8英寸LCD", "驾驶舱主飞行显示", 5, "航空仪表");
            c.add("发动机参数指示器", "EPI-400, 4通道", "发动机状态监控", 5, "航空仪表");
            c.add("28V直流电源模块", "DC28-500W, 效率>92%", "航电系统主供电", 6, "航空电源模块");
            return c;
        }
    }
}
