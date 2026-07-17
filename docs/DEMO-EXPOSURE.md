# 本地敞口回放演示（路径 1）

不依赖 Flink 集群：Console `exposure-demo` 启动时若高危表为空，会自动从 classpath 灌入 Germany vs Paraguay 演示数据。

## 推荐（值班台）

```bash
docker compose up -d redis   # 或本机已有 Redis :6379
./start.sh --exposure-demo --background
```

打开：http://localhost:18088/girisk/exposure  
账号：`admin` / `admin123`

- 启动自灌：`girisk.demo.auto-seed=true`（见 `application-exposure-demo.yml`）
- 空态 / 顶栏也可：`POST /api/v1/sports/replay/demo`（前端「灌入演示数据」「重灌演示」）
- 资源：`girisk-console/src/main/resources/demo/germany-paraguay/{fixture-view.json,sports-seed.json}`
- 在线试算已开：`girisk.sports.online-decide-enabled=true`

赛事页可：**改场次限额**（δ / 种子 / 阈值 / 单注上限）、**停盘/开盘**、下钻盘口明细。

## 重生成 classpath 资源（可选）

改闸门参数或订单 CSV 后，用引擎回放重写资源（不必连 Redis）：

```bash
mvn -pl girisk-engine -am -DskipTests package
java -cp girisk-engine/target/girisk-engine-1.0.0.jar \
  com.girisk.flink.risk.demo.LocalExposureReplayMain \
  --file girisk-engine/src/test/resources/germany-vs-paraguay-orders.csv \
  --skip-redis true \
  --seed-out girisk-console/src/main/resources/demo/germany-paraguay/sports-seed.json
```

完整脚本（回放写 Redis + 启 Console + curl seed）仍可用：

```bash
./scripts/demo-germany-exposure.sh
./scripts/demo-germany-exposure.sh --skip-console   # 只灌 Redis
```

## 预期统计（对齐产品 HTML）

| 指标 | 值 |
|------|-----|
| 接单 | 1964 |
| 拦截 | 767（LIMIT 767 / EXPOSURE 0） |
| 无风控最差盈亏 | ≈ -772,040 @ 0:1 |
| 拦截后最差盈亏 | ≈ -19,792 @ 1:0 |
| 接单本金 | 129,893.96 |

## Kafka 生产者（日后接真 Flink）

```bash
# dry-run
java -cp girisk-engine/target/girisk-engine-1.0.0.jar \
  com.girisk.flink.risk.demo.OrderFileKafkaPublisher \
  --file girisk-engine/src/test/resources/germany-vs-paraguay-orders.csv \
  --dry-run --limit 3

# 真发（需 Kafka）
docker compose up -d kafka
java -cp girisk-engine/target/girisk-engine-1.0.0.jar \
  com.girisk.flink.risk.demo.OrderFileKafkaPublisher \
  --file girisk-engine/src/test/resources/germany-vs-paraguay-orders.csv \
  --bootstrap localhost:9092 \
  --topic girisk.trading.order.risk-check.v1
```

## Redis Key

- `girisk:view:fixture:germany-paraguay`
- `girisk:view:top:worstloss`
- `girisk:override:fixture:{matchCode}`（场次限额覆盖）
