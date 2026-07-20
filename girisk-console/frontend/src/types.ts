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
  /** 重复订单（decision PASS 但 evidence.duplicateIgnored） */
  duplicateCount?: number;
  totalOrders?: number;
  acceptedStakeYuan?: number;
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
  stake: number;
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
