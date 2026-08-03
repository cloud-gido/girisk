export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

export interface FixtureReplayStats {
  acceptedCount?: number;
  rejectedLimit?: number;
  rejectedExposure?: number;
  rejectedTotal?: number;
  /** 重复订单（同一 orderId 的 pre 二次及以上；与 post 确认池无关） */
  duplicateCount?: number;
  totalOrders?: number;
  /** 决策 PASS 累计本金（非返彩；盘口「真实已投注」看 marketGroups.actualStake） */
  acceptedStakeYuan?: number;
  /** 真确认池单数（post CONFIRMED）；≠ acceptedCount */
  confirmedPoolCount?: number;
  /** 真确认池本金 */
  confirmedPoolStakeYuan?: number;
  /** 持险窗口单数（与盘口同源：接单=trial，拒单=confirmed） */
  heldRiskOrderCount?: number;
  heldRiskStakeYuan?: number;
  noRiskWorstPnlYuan?: number;
  noRiskWorstScore?: string;
  withRiskWorstPnlYuan?: number;
  withRiskWorstScore?: string;
  delta?: number;
  seedPayoutYuan?: number;
  maxWorstLossYuan?: number;
}

export interface RiskFixtureView {
  id: number;
  fixtureId: string;
  homeTeam: string;
  awayTeam: string;
  operatorId?: string;
  /** Redis hash：真确认池单数（post CONFIRMED） */
  confirmedOrders: number;
  pendingReserved: number;
  worstLossCents: number;
  worstScore?: string;
  liveScore?: string;
  marketSummaryJson?: string;
  riskLevel: string;
  updatedAt: string;
  replayStats?: FixtureReplayStats | null;
  /** Flink Gate1 盘口快照（与责任盘同源） */
  marketGroups?: MarketGroupView[] | null;
  limitDelta?: number | null;
  initialSeedPayoutYuan?: number | null;
  marketGroupsUpdatedAt?: number | null;
  /** GROUPING SETS：pre / live 段视图（整场仍用顶层字段） */
  segments?: {
    pre?: FixtureSegmentView | null;
    live?: FixtureSegmentView | null;
  } | null;
}

export interface FixtureSegmentView {
  segment?: string;
  empty?: boolean;
  /** 真确认池单数 */
  confirmedOrders?: number;
  replayStats?: FixtureReplayStats | null;
  marketGroups?: MarketGroupView[] | Array<Record<string, unknown>> | null;
  worstLossCents?: number;
  worstScore?: string;
}

export interface DashboardOverview {
  totalDecisions: number;
  passCount: number;
  rejectCount: number;
  reviewCount: number;
  limitCount?: number;
  pendingCases: number;
  activeRules: number;
  listEntries: number;
  publishedConfigEpoch?: number;
  highRiskFixtures?: number;
  avgLatencyMs: number;
  decisionTrend: { decision: string; cnt: number }[];
  riskDistribution: { level: string; cnt: number }[];
  topFixtures?: RiskFixtureView[];
  recentRejectReasons?: { decision: string; reason: string; cnt: number }[];
}

export interface RiskEvaluateRequest {
  orderId: string;
  userId: string;
  amount: number;
  currency?: string;
  paymentMethod?: string;
  ip?: string;
  deviceId?: string;
  merchantId?: string;
  productCategory?: string;
  country?: string;
  orderCount24h?: number;
  amountSum24h?: number;
  isNewUser?: boolean;
  deviceRiskScore?: number;
  scenario?: string;
}

export interface RiskEvaluateResponse {
  requestId: string;
  orderId: string;
  decision: 'PASS' | 'REJECT' | 'REVIEW' | 'CHALLENGE';
  riskScore: number;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  hitRules: string[];
  reason: string;
  strategyCode: string;
  latencyMs: number;
  caseNo?: string;
}

export interface RiskStrategy {
  id: number;
  code: string;
  name: string;
  scenario: string;
  description?: string;
  enabled: boolean;
  priority: number;
}

export interface RiskRule {
  id: number;
  strategyId: number;
  code: string;
  name: string;
  ruleType: string;
  field?: string;
  operator?: string;
  threshold?: string;
  action: string;
  scoreWeight: number;
  priority: number;
  enabled: boolean;
  description?: string;
}

export interface RiskListEntry {
  id: number;
  listType: string;
  listKey: string;
  listValue: string;
  reason?: string;
  source?: string;
  enabled: boolean;
}

export interface RiskCase {
  id: number;
  caseNo: string;
  decisionLogId?: number;
  orderId: string;
  userId: string;
  operatorId?: string;
  status: string;
  priority: string;
  riskScore: number;
  riskLevel: string;
  assignee?: string;
  reviewDecision?: string;
  reviewComment?: string;
  slaDeadline?: string;
  callbackStatus?: string;
  callbackPayload?: string;
  callbackAt?: string;
  createdAt: string;
  reviewedAt?: string;
}

export interface DecisionReason {
  ruleId: string;
  ruleVersion: number;
  stage: string;
  action: string;
  message: string;
  evidence?: Record<string, unknown>;
}

export interface DecisionVersions {
  configEpoch?: number;
  paramSetVersion?: string;
  ruleSetVersion?: string;
  engineBuild?: string;
}

export interface DecisionGateSummary {
  rejectReason?: string;
  limitRejected?: boolean;
  exposureRejected?: boolean;
  gate1?: {
    selectionLabel?: string;
    groupKey?: string;
    proposedPayoutYuan?: number;
    stakeBeforeYuan?: number;
    targetAmountYuan?: number;
    maxAllowedYuan?: number;
    acceptMaxYuan?: number;
    limitDelta?: number;
    seedPayoutYuan?: number;
    overLimitBefore?: boolean;
  };
  gate2?: {
    trialWorstLossYuan?: number;
    maxWorstLossYuan?: number;
    worstScore?: string;
    beforeWorstPnlYuan?: number;
    trialWorstPnlYuan?: number;
    afterWorstPnlYuan?: number;
    exceeded?: boolean;
  };
}

export interface RiskDecisionLog {
  id: number;
  requestId: string;
  orderId: string;
  userId: string;
  scenario: string;
  strategyCode: string;
  decision: string;
  riskScore: number;
  riskLevel: string;
  hitRules: string;
  reason: string;
  amount: number;
  ip?: string;
  deviceId?: string;
  latencyMs?: number;
  source?: string;
  traceId?: string;
  fixtureId?: string;
  operatorId?: string;
  marketJson?: string;
  stakeCents?: number;
  odds?: string;
  payoutCents?: number;
  maxAcceptableStakeCents?: number;
  reasonsJson?: string;
  versionsJson?: string;
  featureSnapshotJson?: string;
  evidenceJson?: string;
  createdAt: string;
}

export interface RiskConfigRelease {
  id: number;
  configEpoch: number;
  scope: string;
  status: string;
  paramSetVersion: string;
  ruleSetVersion: string;
  paramSetJson: string;
  ruleSetJson: string;
  changeSummary?: string;
  createdBy: string;
  submittedBy?: string;
  approvedBy?: string;
  publishedBy?: string;
  approvalTicket?: string;
  rejectReason?: string;
  createdAt: string;
  submittedAt?: string;
  approvedAt?: string;
  publishedAt?: string;
}

export interface RiskDecisionResponse {
  traceId: string;
  requestId: string;
  orderId: string;
  decision: string;
  riskScore: number;
  riskLevel: string;
  reasons: DecisionReason[];
  versions: DecisionVersions;
  featureSnapshot: Record<string, unknown>;
  maxAcceptableStakeCents?: number;
  payoutCents?: number;
  fixtureId?: string;
  operatorId?: string;
  strategyCode?: string;
  latencyMs: number;
  caseNo?: string;
  reason: string;
  limitMode?: boolean;
  sportsDetail?: Record<string, unknown>;
}

export interface DecisionReplay {
  decision: RiskDecisionLog;
  history: RiskDecisionLog[];
  reasons: DecisionReason[];
  versions: DecisionVersions;
  featureSnapshot: Record<string, unknown>;
  evidence?: Record<string, unknown>;
  gateSummary?: DecisionGateSummary;
  market: Record<string, unknown>;
  case?: RiskCase;
  events?: RiskEvent[];
  configRelease?: RiskConfigRelease;
  explainable: boolean;
  /** doris = Kafka 原样审计；postgres = Console 运营库回退 */
  auditSource?: 'doris' | 'postgres' | 'mysql';
}

/** Doris 审计库配置（用户填主机/端口/库名/账密/表名；内部拼 jdbc:mysql） */
export interface DorisAuditConfigView {
  enabled: boolean;
  host: string;
  port: number;
  database: string;
  username: string;
  passwordSet: boolean;
  decisionTable: string;
  configTable: string;
  jdbcUrl?: string;
  available: boolean;
  activeSource: 'doris' | 'postgres';
  lastError?: string;
}

export interface DorisAuditConfigRequest {
  enabled?: boolean;
  host?: string;
  port?: number;
  database?: string;
  username?: string;
  /** 空字符串 = 无密码 */
  password?: string;
  decisionTable?: string;
  configTable?: string;
}

export interface StreamStatus {
  kafkaEnabled: boolean;
  bootstrapServers?: string;
  orderTopic?: string;
  decisionTopic?: string;
  processedCount: number;
  errorCount: number;
  sseSubscribers: number;
  mode: string;
}

export interface RiskEvent {
  id: number;
  eventType: string;
  severity: string;
  orderId?: string;
  userId?: string;
  title: string;
  detail?: string;
  createdAt: string;
}

export interface OverLimitOutcomeItem {
  matchCode: string;
  homeTeam: string;
  awayTeam: string;
  marketType: string;
  marketLabel: string;
  line: string;
  selection: string;
  stake: number;
  maxAllowedAmount: number;
}

export interface SportsDashboardSummary {
  matchCount: number;
  outcomeCount: number;
  overLimitOutcomeCount: number;
  limitModeMatchCount: number;
  totalStake: number;
  matches: SportsMatch[];
  overLimitOutcomes: OverLimitOutcomeItem[];
}

export interface SportsMatch {
  id: number;
  matchCode: string;
  homeTeam: string;
  awayTeam: string;
  sportCode?: string;
  leagueCode?: string;
  leagueName?: string;
  exposureThreshold: number;
  limitMode: boolean;
  currentExposure: number;
  delta: number;
  seedPayoutYuan?: number;
  maxWorstLossYuan?: number;
  maxBetPayoutYuan?: number | null;
  overrideActive?: boolean;
  status: string;
  lastCheckAt?: string;
}

/** 敞口值班台列表行（含有效门控 + Redis live） */
export interface SportsMatchListRow extends SportsMatch {
  updatedAt?: string;
  tradingEnabled: boolean;
  limitGateEnabled: boolean;
  exposureGateEnabled: boolean;
  tradingSource?: string;
  limitGateSource?: string;
  exposureGateSource?: string;
  liveScore?: string | null;
  worstScore?: string | null;
  worstLossCents?: number | null;
  riskLevel?: string | null;
  confirmedOrders?: number | null;
}

export interface SportsMatchMetaRequest {
  homeTeam?: string | null;
  awayTeam?: string | null;
  sportCode?: string | null;
  leagueCode?: string | null;
  leagueName?: string | null;
}

export interface SportsMatchListQuery {
  sportCode?: string;
  leagueCode?: string;
  matchCode?: string;
  q?: string;
  status?: string;
  limitMode?: boolean;
  gateOff?: string;
}

/** 场次限额覆盖：有效值 + 原始覆盖字段 */
export interface FixtureLimitParamsView {
  matchCode: string;
  delta: number;
  seedPayoutYuan: number;
  maxWorstLossYuan: number;
  maxBetPayoutYuan?: number | null;
  overrideActive: boolean;
  baseDelta?: number;
  baseExposureThreshold?: number;
  globalSeedPayoutYuan?: number;
  globalMaxWorstLossYuan?: number;
  globalMaxBetPayoutYuan?: number | null;
  overrideDelta?: number | null;
  overrideSeedPayoutYuan?: number | null;
  overrideMaxWorstLossYuan?: number | null;
  overrideMaxBetPayoutYuan?: number | null;
  updatedBy?: string;
  updatedAt?: string;
}

export interface FixtureLimitOverrideRequest {
  delta?: number | null;
  seedPayoutYuan?: number | null;
  maxWorstLossYuan?: number | null;
  maxBetPayoutYuan?: number | null;
  operatorId?: string;
}

/** 总体 / 球类 / 联赛层级限额 */
export interface ScopeLimitParamsView {
  scopeType: string;
  scopeKey: string;
  delta: number;
  seedPayoutYuan: number;
  maxWorstLossYuan: number;
  maxBetPayoutYuan?: number | null;
  overrideActive: boolean;
  inheritedDelta?: number;
  inheritedSeedPayoutYuan?: number;
  inheritedMaxWorstLossYuan?: number;
  inheritedMaxBetPayoutYuan?: number | null;
  overrideDelta?: number | null;
  overrideSeedPayoutYuan?: number | null;
  overrideMaxWorstLossYuan?: number | null;
  overrideMaxBetPayoutYuan?: number | null;
  updatedBy?: string;
  updatedAt?: string;
}

/** 总体 / 球类 / 联赛批量停盘汇总 */
export interface ScopeTradingStatusSummary {
  scopeType: string;
  scopeKey: string;
  matchCount: number;
  suspendedCount: number;
  activeCount: number;
  status?: 'ACTIVE' | 'SUSPENDED';
  updated?: number;
}

/** 总开关 / 限额开关 / 敞口开关（继承：单赛事 > 联赛 > 球类 > 默认） */
export interface ScopeGateParamsView {
  scopeType: string;
  scopeKey: string;
  tradingEnabled: boolean;
  limitGateEnabled: boolean;
  exposureGateEnabled: boolean;
  tradingSource: string;
  limitGateSource: string;
  exposureGateSource: string;
  overrideActive: boolean;
  overrideTradingEnabled?: boolean | null;
  overrideLimitGateEnabled?: boolean | null;
  overrideExposureGateEnabled?: boolean | null;
  inheritedTradingEnabled: boolean;
  inheritedLimitGateEnabled: boolean;
  inheritedExposureGateEnabled: boolean;
  canWrite: boolean;
  updatedBy?: string;
  updatedAt?: string;
}

export interface ScopeGateOverrideRequest {
  tradingEnabled?: boolean | null;
  limitGateEnabled?: boolean | null;
  exposureGateEnabled?: boolean | null;
  operatorId?: string;
}

export interface OutcomeLimitRow {
  selection: string;
  /** 展示用：真实已投注（不含冷启动） */
  stake: number;
  /** 冷启动种子（每盘口） */
  seedYuan?: number;
  /** 真实已投注（Flink actualStake；优先于从 book 反推） */
  actualStake?: number;
  /** 含种子账面（限额公式用） */
  bookStake?: number;
  targetAmount: number;
  maxAllowedAmount: number;
  acceptMax: number;
}

export interface MarketGroupView {
  marketType: string;
  marketLabel: string;
  line: string;
  stakes: Record<string, number>;
  /** 还能接收 b_max */
  limits: Record<string, number>;
  outcomes: OutcomeLimitRow[];
}

export interface SportsMatchView extends SportsMatch {
  marketGroups: MarketGroupView[];
  sportCode?: string;
  leagueCode?: string;
  leagueName?: string;
}

export interface SportsBetEvaluateResponse {
  requestId: string;
  orderId: string;
  decision: string;
  reason: string;
  limitMode: boolean;
  amount: number;
  maxAcceptAmount: number;
  currentStake: number;
  groupTotal: number;
  targetAmount: number;
  maxAllowedAmount: number;
  groupStakes: Record<string, number>;
  groupLimits: Record<string, number>;
  groupTargets: Record<string, number>;
  groupMaxAllowed: Record<string, number>;
  matchExposure: number;
  exposureThreshold: number;
  latencyMs: number;
}

export interface SportsBetLog {
  id: number;
  requestId: string;
  orderId: string;
  matchCode: string;
  marketType: string;
  lineValue?: string;
  selection: string;
  amount: number;
  decision: string;
  maxAccept?: number;
  limitMode: boolean;
  reason?: string;
  createdAt: string;
}
