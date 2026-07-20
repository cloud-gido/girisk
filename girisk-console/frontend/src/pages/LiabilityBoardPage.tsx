import {
  AlertOutlined,
  AppstoreOutlined,
  ArrowLeftOutlined,
  DollarOutlined,
  DownOutlined,
  RightOutlined,
  TrophyOutlined,
} from '@ant-design/icons';
import {
  Alert,
  Breadcrumb,
  Button,
  Card,
  Col,
  Collapse,
  Empty,
  Row,
  Segmented,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import ScopeDutyConfigPanel from '../components/ScopeDutyConfigPanel';
import ScopeGateDutyBar from '../components/ScopeGateDutyBar';
import ScopeLimitDutyBar from '../components/ScopeLimitDutyBar';
import { sportsApi } from '../api/sportsClient';
import { riskApi } from '../api/riskClient';
import type {
  FixtureReplayStats,
  MarketGroupView,
  OutcomeLimitRow,
  OverLimitOutcomeItem,
  RiskFixtureView,
  SportsDashboardSummary,
  SportsMatch,
  SportsMatchView,
} from '../types';
import { buildOutcomeRows, groupStakeTotal } from '../utils/proportionalLimit';
import { resolveMatchFromFixture } from '../utils/exposureNav';
import {
  readExposureNav,
  writeExposureNav,
  type ExposureLevel,
} from '../utils/exposureNavStorage';
import { selectionLabel } from '../utils/sportsLabels';
import { outcomeLimitColumns } from '../utils/sportsLimitColumns';
import { LevelTag } from '../utils/tags';

/** 总体 / 球类 / 联赛 / 赛事：列表内展开看本场汇总，减少整页跳转 */
type Level = ExposureLevel;

type MatchExpandCache = Record<
  string,
  {
    loading: boolean;
    error?: string;
    view?: SportsMatchView;
    fixture?: RiskFixtureView | null;
  }
>;

const SPORT_META: Record<string, { label: string; emoji: string }> = {
  football: { label: '足球', emoji: '⚽' },
  basketball: { label: '篮球', emoji: '🏀' },
};

function sportMeta(code: string) {
  return SPORT_META[code] ?? { label: code, emoji: '🏅' };
}

function ofSport(m: SportsMatch) {
  return m.sportCode || 'football';
}

function ofLeague(m: SportsMatch) {
  return m.leagueCode || 'UNKNOWN';
}

function leagueTitle(m: SportsMatch) {
  return m.leagueName || m.leagueCode || '未分组联赛';
}

function marketKeyOf(g: MarketGroupView) {
  return `${g.marketType}|${g.line || ''}`;
}

export default function LiabilityBoardPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const sportId = params.get('sport') || undefined;
  const leagueId = params.get('league') || undefined;
  const matchCode = params.get('match') || undefined;
  const filterOver = params.get('filter') === 'over';
  const levelParam = params.get('level') as Level | null;

  const level: Level = levelParam
    || (matchCode ? 'match' : leagueId ? 'league' : sportId ? 'sport' : 'overall');

  const [dash, setDash] = useState<SportsDashboardSummary | null>(null);
  const [fixtures, setFixtures] = useState<RiskFixtureView[]>([]);
  const [detail, setDetail] = useState<SportsMatchView | null>(null);
  const [matchFixture, setMatchFixture] = useState<RiskFixtureView | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [statusSaving, setStatusSaving] = useState(false);
  const [demoLoading, setDemoLoading] = useState(false);
  const [hydrated, setHydrated] = useState(false);
  const [expandedMatchCodes, setExpandedMatchCodes] = useState<string[]>([]);
  const [expandCache, setExpandCache] = useState<MatchExpandCache>({});
  const [pendingExpandCode, setPendingExpandCode] = useState<string | null>(null);

  const setQuery = useCallback((next: Record<string, string | undefined>) => {
    const sp = new URLSearchParams();
    Object.entries(next).forEach(([k, v]) => {
      if (v) sp.set(k, v);
    });
    setParams(sp, { replace: false });
    writeExposureNav({
      level: (next.level as Level) || 'overall',
      sport: next.sport,
      league: next.league,
      match: next.match,
      filter: next.filter,
    });
  }, [setParams]);

  const openMatch = useCallback(async (code: string, silent = false) => {
    if (!silent) {
      setDetailLoading(true);
      setDetailError(null);
      setMatchFixture(null);
    }
    try {
      const view = await sportsApi.match(code);
      setDetail(view);
      try {
        setMatchFixture(await riskApi.fixture(code));
      } catch {
        if (!silent) setMatchFixture(null);
      }
    } catch (e) {
      if (!silent) {
        setDetail(null);
        setDetailError((e as Error).message || '加载赛事失败');
      }
    } finally {
      if (!silent) setDetailLoading(false);
    }
  }, []);

  const refresh = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const [d, fx] = await Promise.all([
        sportsApi.dashboard(),
        riskApi.fixturesTop(8).catch(() => [] as RiskFixtureView[]),
      ]);
      setDash(d);
      setFixtures(fx);
    } catch (e) {
      if (!silent) message.error((e as Error).message);
    } finally {
      if (!silent) setLoading(false);
    }
  }, []);

  useEffect(() => { void refresh(false); }, [refresh]);

  // 敞口看板：取 Redis 最近事实即可，刷新可慢一点（15s）
  useEffect(() => {
    const id = window.setInterval(() => {
      if (document.visibilityState === 'hidden') return;
      void refresh(true);
      if (level === 'match' && matchCode) {
        void openMatch(matchCode, true);
      }
    }, 5_000);
    return () => window.clearInterval(id);
  }, [refresh, openMatch, level, matchCode]);

  // 无 URL 参数时恢复上次选择
  useEffect(() => {
    if (hydrated) return;
    const hasUrl = !!(levelParam || sportId || leagueId || matchCode || filterOver);
    if (!hasUrl) {
      const saved = readExposureNav();
      if (saved?.level) {
        setQuery({
          // 默认回到列表层，不自动恢复值班深页
          level: saved.level === 'match' ? 'match' : saved.level,
          sport: saved.sport,
          league: saved.league,
          filter: saved.filter,
        });
      } else {
        setQuery({ level: 'match' });
      }
    }
    setHydrated(true);
  }, [hydrated, levelParam, sportId, leagueId, matchCode, filterOver, setQuery]);

  useEffect(() => {
    if (level === 'match' && matchCode) openMatch(matchCode);
    else {
      setDetail(null);
      setDetailError(null);
    }
  }, [level, matchCode, openMatch]);

  // 进赛事详情时补齐球类/联赛，便于面包屑
  useEffect(() => {
    if (!detail || !matchCode || level !== 'match') return;
    if (sportId && leagueId && levelParam === 'match') return;
    setQuery({
      level: 'match',
      sport: detail.sportCode || sportId || 'football',
      league: detail.leagueCode || leagueId || 'UNKNOWN',
      match: matchCode,
      filter: filterOver ? 'over' : undefined,
    });
  }, [detail, matchCode, sportId, leagueId, filterOver, level, levelParam, setQuery]);

  const allMatches = dash?.matches ?? [];

  const sportStats = useMemo(() => {
    const codes = Array.from(new Set(allMatches.map(ofSport)));
    if (!codes.includes('football')) codes.unshift('football');
    if (!codes.includes('basketball')) codes.push('basketball');
    return codes.map((code) => {
      const list = allMatches.filter((m) => ofSport(m) === code);
      const leagues = new Set(list.map(ofLeague));
      return {
        id: code,
        ...sportMeta(code),
        matchCount: list.length,
        leagueCount: leagues.size,
        totalStake: list.reduce((s, m) => s + Number(m.currentExposure || 0), 0),
        limitModeCount: list.filter((m) => m.limitMode).length,
      };
    });
  }, [allMatches]);

  /** 当前球类下的联赛；球类未选时 = 全部联赛 */
  const leagueStats = useMemo(() => {
    const list = sportId ? allMatches.filter((m) => ofSport(m) === sportId) : allMatches;
    const map = new Map<string, { id: string; name: string; sport: string; matches: SportsMatch[] }>();
    for (const m of list) {
      const id = ofLeague(m);
      const sport = ofSport(m);
      const key = `${sport}:${id}`;
      if (!map.has(key)) map.set(key, { id, name: leagueTitle(m), sport, matches: [] });
      map.get(key)!.matches.push(m);
    }
    return Array.from(map.values()).map((g) => ({
      ...g,
      matchCount: g.matches.length,
      totalStake: g.matches.reduce((s, m) => s + Number(m.currentExposure || 0), 0),
      limitModeCount: g.matches.filter((m) => m.limitMode).length,
    }));
  }, [allMatches, sportId]);

  const leagueMatches = useMemo(() => {
    if (!leagueId) return [];
    return allMatches.filter((m) => {
      if (ofLeague(m) !== leagueId) return false;
      if (sportId && ofSport(m) !== sportId) return false;
      return true;
    });
  }, [allMatches, sportId, leagueId]);

  const matchList = useMemo(() => {
    let list = allMatches;
    if (filterOver) list = list.filter((m) => m.limitMode);
    return list;
  }, [allMatches, filterOver]);

  const currentLeagueName = useMemo(() => {
    const hit = leagueStats.find((l) => l.id === leagueId && (!sportId || l.sport === sportId));
    return hit?.name || leagueId || '';
  }, [leagueId, leagueStats, sportId]);

  const enterMatch = (m: SportsMatch) => {
    setQuery({
      level: 'match',
      sport: ofSport(m),
      league: ofLeague(m),
      match: m.matchCode,
      filter: filterOver ? 'over' : undefined,
    });
  };

  const loadMatchExpand = useCallback(async (code: string, force = false) => {
    let shouldFetch = true;
    setExpandCache((prev) => {
      if (!force && (prev[code]?.loading || prev[code]?.view)) {
        shouldFetch = false;
        return prev;
      }
      return {
        ...prev,
        [code]: {
          loading: true,
          view: prev[code]?.view,
          fixture: prev[code]?.fixture,
          error: undefined,
        },
      };
    });
    if (!shouldFetch) return;
    try {
      const view = await sportsApi.match(code);
      let fixture: RiskFixtureView | null = null;
      try {
        fixture = await riskApi.fixture(code);
      } catch {
        fixture = null;
      }
      setExpandCache((prev) => ({ ...prev, [code]: { loading: false, view, fixture } }));
    } catch (e) {
      setExpandCache((prev) => ({
        ...prev,
        [code]: {
          loading: false,
          view: prev[code]?.view,
          fixture: prev[code]?.fixture,
          error: (e as Error).message || '加载失败',
        },
      }));
    }
  }, []);

  const toggleMatchExpand = useCallback((m: SportsMatch) => {
    const code = m.matchCode;
    setExpandedMatchCodes((prev) => {
      const open = prev.includes(code);
      if (open) return prev.filter((c) => c !== code);
      void loadMatchExpand(code);
      return [code];
    });
  }, [loadMatchExpand]);

  const matchListExpandable = useMemo((): TableProps<SportsMatch>['expandable'] => ({
    expandedRowKeys: expandedMatchCodes,
    onExpand: (expanded, record) => {
      if (expanded) {
        setExpandedMatchCodes([record.matchCode]);
        void loadMatchExpand(record.matchCode);
      } else {
        setExpandedMatchCodes((prev) => prev.filter((c) => c !== record.matchCode));
      }
    },
    expandIcon: ({ expanded, onExpand, record }) => (
      <Button
        type="text"
        size="small"
        aria-label={expanded ? '收起' : '展开'}
        icon={<DownOutlined rotate={expanded ? 180 : 0} style={{ transition: 'transform .2s' }} />}
        onClick={(e) => {
          e.stopPropagation();
          onExpand(record, e);
        }}
      />
    ),
    expandedRowRender: (m) => (
      <MatchInlinePanel
        matchCode={m.matchCode}
        cache={expandCache[m.matchCode]}
        onRetry={() => void loadMatchExpand(m.matchCode, true)}
        onOpenDuty={() => enterMatch(m)}
        onBetTrial={() => navigate(`/girisk/sandbox/bet?match=${m.matchCode}`)}
      />
    ),
  }), [expandedMatchCodes, expandCache, loadMatchExpand, navigate]);

  useEffect(() => {
    if (expandedMatchCodes.length === 0) return;
    const id = window.setInterval(() => {
      if (document.visibilityState === 'hidden') return;
      for (const code of expandedMatchCodes) {
        void loadMatchExpand(code, true);
      }
    }, 5_000);
    return () => window.clearInterval(id);
  }, [expandedMatchCodes, loadMatchExpand]);

  const openFixture = (r: RiskFixtureView) => {
    const m = resolveMatchFromFixture(r, allMatches);
    if (!m) {
      message.warning(`场次 ${r.fixtureId} 尚未接入敞口赛事库，无法下钻盘口`);
      return;
    }
    if (level === 'match' && !matchCode) {
      toggleMatchExpand(m);
      return;
    }
    // 总览等高危入口：先进列表再展开，不跳值班深页
    setPendingExpandCode(m.matchCode);
    setQuery({
      level: 'match',
      sport: ofSport(m),
      league: ofLeague(m),
      filter: filterOver ? 'over' : undefined,
    });
  };

  useEffect(() => {
    if (!pendingExpandCode || level !== 'match' || matchCode) return;
    const m = allMatches.find((x) => x.matchCode === pendingExpandCode);
    if (!m) return;
    toggleMatchExpand(m);
    setPendingExpandCode(null);
  }, [pendingExpandCode, level, matchCode, allMatches, toggleMatchExpand]);

  const rowsForGroup = (g: MarketGroupView) => {
    if (g.outcomes?.length) {
      return g.outcomes.map((o) => ({
        selection: o.selection,
        stake: Number(o.stake) || 0,
        targetAmount: Number(o.targetAmount) || 0,
        maxAllowedAmount: Number(o.maxAllowedAmount) || 0,
        acceptMax: Number(o.acceptMax) || 0,
      }));
    }
    return buildOutcomeRows(
      Object.keys(g.stakes || {}),
      g.stakes || {},
      Number(detail?.delta) || 0.2,
      Number(detail?.seedPayoutYuan) || 0,
    );
  };

  const flinkMarketTotal = useMemo(() => {
    if (!detail) return 0;
    return Math.round(detail.marketGroups.reduce((sum, g) => sum + groupStakeTotal(g.stakes || {}), 0) * 100) / 100;
  }, [detail]);

  const runCheck = async (code: string) => {
    try {
      const view = await sportsApi.exposureCheck(code);
      setDetail(view);
      message.success('已刷新赛事敞口');
      refresh();
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  const toggleSuspend = async () => {
    if (!detail) return;
    const next = detail.status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED';
    setStatusSaving(true);
    try {
      const view = await sportsApi.setMatchStatus(detail.matchCode, next);
      setDetail(view);
      message.success(next === 'SUSPENDED' ? '已停盘' : '已开盘');
      refresh();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setStatusSaving(false);
    }
  };

  const loadDemoData = async (force = false) => {
    setDemoLoading(true);
    try {
      const r = await sportsApi.loadDemoReplay(force);
      if (r.loaded) {
        message.success('演示数据已灌入');
      } else {
        message.info(force ? '已重灌' : '高危表已有数据，未覆盖（可强制重灌）');
      }
      await refresh();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setDemoLoading(false);
    }
  };

  /** 顶栏切换：赛事层默认进列表（行内展开），不自动跳值班深页 */
  const goLevel = (v: Level) => {
    const f = filterOver ? 'over' : undefined;
    const saved = readExposureNav();
    if (v === 'overall') {
      setQuery({ level: 'overall', filter: f });
      return;
    }
    if (v === 'sport') {
      setQuery({
        level: 'sport',
        sport: sportId || saved?.sport,
        filter: f,
      });
      return;
    }
    if (v === 'league') {
      setQuery({
        level: 'league',
        sport: sportId || saved?.sport,
        league: leagueId || saved?.league,
        filter: f,
      });
      return;
    }
    setQuery({
      level: 'match',
      sport: sportId || saved?.sport,
      league: leagueId || saved?.league,
      filter: f,
    });
  };

  const contextLabel = useMemo(() => {
    if (level === 'overall') return '平台总览';
    if (level === 'sport') {
      return sportId ? `${sportMeta(sportId).label} · 球类` : '选择球类';
    }
    if (level === 'league') {
      if (!leagueId) return '选择联赛';
      return `${currentLeagueName || leagueId} · 联赛`;
    }
    if (matchCode && detail) return `${detail.homeTeam} vs ${detail.awayTeam}`;
    if (matchCode) return matchCode;
    return filterOver ? '超额赛事' : '赛事值班台';
  }, [level, sportId, leagueId, currentLeagueName, matchCode, detail, filterOver]);

  if (loading && !dash) {
    return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />;
  }

  return (
    <div className="liability-board">
      <header className="liability-chrome">
        <div className="liability-chrome-main">
          <div className="liability-chrome-title">
            <h2>敞口看板</h2>
            <p>主路径：赛事列表 → 展开看本场事实。限额按层继承（赛事 &gt; 联赛 &gt; 球类 &gt; 总体）。</p>
          </div>
          <Space wrap className="liability-chrome-actions">
            <Segmented
              size="small"
              value={filterOver ? 'over' : 'all'}
              onChange={(v) => setQuery({
                level,
                sport: sportId,
                league: leagueId,
                match: matchCode,
                filter: v === 'over' ? 'over' : undefined,
              })}
              options={[
                { label: '全部', value: 'all' },
                { label: '仅超额', value: 'over' },
              ]}
            />
            <Button size="small" loading={demoLoading} onClick={() => loadDemoData(true)}>重灌演示</Button>
            <Button size="small" type="primary" ghost onClick={() => { void refresh(false); }}>刷新</Button>
          </Space>
        </div>
        <div className="liability-chrome-nav">
          <Segmented
            value={level}
            onChange={(v) => goLevel(v as Level)}
            options={[
              { label: '总览', value: 'overall' },
              { label: '球类', value: 'sport' },
              { label: '联赛', value: 'league' },
              { label: '赛事值班', value: 'match' },
            ]}
          />
          <div className="liability-chrome-context">
            <Typography.Text strong>{contextLabel}</Typography.Text>
            {(level !== 'overall' || matchCode) && (
              <Breadcrumb
                className="liability-chrome-crumb"
                items={[
                  { title: <a onClick={() => setQuery({ level: 'overall', filter: filterOver ? 'over' : undefined })}>总览</a> },
                  ...(sportId && level !== 'overall' ? [{
                    title: (
                      <a onClick={() => setQuery({ level: 'sport', sport: sportId, filter: filterOver ? 'over' : undefined })}>
                        {sportMeta(sportId).label}
                      </a>
                    ),
                  }] : []),
                  ...(leagueId && (level === 'league' || level === 'match') ? [{
                    title: (
                      <a onClick={() => setQuery({
                        level: 'league',
                        sport: sportId,
                        league: leagueId,
                        filter: filterOver ? 'over' : undefined,
                      })}
                      >
                        {currentLeagueName || leagueId}
                      </a>
                    ),
                  }] : []),
                  ...(level === 'match' ? [{
                    title: (
                      <a onClick={() => setQuery({
                        level: 'match',
                        sport: sportId,
                        league: leagueId,
                        filter: filterOver ? 'over' : undefined,
                      })}
                      >
                        赛事列表
                      </a>
                    ),
                  }] : []),
                  ...(matchCode && level === 'match' ? [{
                    title: detail ? `${detail.homeTeam} vs ${detail.awayTeam}` : matchCode,
                  }] : []),
                ]}
              />
            )}
          </div>
        </div>
      </header>

      {level === 'overall' && dash && (
        <div className="liability-pane">
          <Card className="liability-hero content-card" size="small">
            <div className="liability-hero-row">
              <div>
                <Typography.Title level={4} style={{ margin: 0 }}>今日值班入口</Typography.Title>
                <Typography.Text type="secondary">
                  直接进入赛事列表，点行展开看拦截汇总与盘口，无需层层下钻。
                </Typography.Text>
              </div>
              <Space wrap>
                <Button
                  type="primary"
                  size="large"
                  onClick={() => setQuery({ level: 'match', filter: filterOver ? 'over' : undefined })}
                >
                  打开赛事值班台 <RightOutlined />
                </Button>
                <Button size="large" onClick={() => setQuery({ level: 'match', filter: 'over' })}>
                  仅看超额
                </Button>
              </Space>
            </div>
          </Card>

          <Row gutter={[12, 12]} style={{ marginTop: 16 }}>
            <Col xs={12} sm={12} lg={6}>
              <Card className="stat-card liability-stat" hoverable onClick={() => setQuery({ level: 'match', filter: filterOver ? 'over' : undefined })}>
                <Typography.Text type="secondary">在管赛事</Typography.Text>
                <div className="liability-kpi"><TrophyOutlined /> {dash.matchCount}</div>
              </Card>
            </Col>
            <Col xs={12} sm={12} lg={6}>
              <Card className="stat-card liability-stat">
                <Typography.Text type="secondary">盘口数</Typography.Text>
                <div className="liability-kpi" style={{ color: '#1677ff' }}><AppstoreOutlined /> {dash.outcomeCount}</div>
              </Card>
            </Col>
            <Col xs={12} sm={12} lg={6}>
              <Card className="stat-card liability-stat" hoverable onClick={() => setQuery({ level: 'match', filter: 'over' })}>
                <Typography.Text type="secondary">超额盘口</Typography.Text>
                <div className="liability-kpi" style={{ color: dash.overLimitOutcomeCount ? '#cf1322' : undefined }}>
                  <AlertOutlined /> {dash.overLimitOutcomeCount}
                </div>
              </Card>
            </Col>
            <Col xs={12} sm={12} lg={6}>
              <Card className="stat-card liability-stat">
                <Typography.Text type="secondary">总投注（本金）</Typography.Text>
                <div className="liability-kpi"><DollarOutlined /> {Number(dash.totalStake).toLocaleString()}</div>
              </Card>
            </Col>
          </Row>

          <ScopeDutyConfigPanel
            mode="overall"
            label="平台总体"
            onSaved={refresh}
          />

          <div className="liability-section-head">
            <Typography.Title level={5} style={{ margin: 0 }}>球类速览</Typography.Title>
            <Typography.Text type="secondary">点选进入球类 / 联赛配置限额与停盘</Typography.Text>
          </div>
          <Row gutter={[12, 12]}>
            {sportStats.map((s) => (
              <Col xs={24} md={12} key={s.id}>
                <button
                  type="button"
                  className="liability-sport-tile"
                  onClick={() => setQuery({ level: 'sport', sport: s.id, filter: filterOver ? 'over' : undefined })}
                >
                  <div className="liability-sport-tile-top">
                    <span className="liability-sport-tile-name">{s.emoji} {s.label}</span>
                    <RightOutlined />
                  </div>
                  <div className="liability-sport-tile-meta">
                    <span>{s.leagueCount} 联赛</span>
                    <span>{s.matchCount} 场</span>
                    <span>投注 {s.totalStake.toLocaleString()}</span>
                    <span>限额中 {s.limitModeCount}</span>
                  </div>
                </button>
              </Col>
            ))}
          </Row>

          <Card
            className="content-card"
            title="Redis 高危快照"
            extra={(
              <Button type="link" onClick={() => setQuery({ level: 'match', filter: filterOver ? 'over' : undefined })}>
                去赛事列表
              </Button>
            )}
            style={{ marginTop: 20 }}
          >
            <Table
              size="small"
              rowKey="fixtureId"
              pagination={false}
              dataSource={fixtures}
              locale={{
                emptyText: (
                  <Empty description="暂无高危赛事快照">
                    <Button type="primary" loading={demoLoading} onClick={() => loadDemoData(false)}>
                      灌入演示数据
                    </Button>
                  </Empty>
                ),
              }}
              columns={[
                {
                  title: '场次',
                  render: (_: unknown, r: RiskFixtureView) => (
                    <Button type="link" style={{ padding: 0 }} onClick={() => openFixture(r)}>
                      {r.homeTeam} vs {r.awayTeam}
                      <Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                        {r.fixtureId}
                      </Typography.Text>
                    </Button>
                  ),
                },
                { title: '接单', width: 70, dataIndex: 'confirmedOrders' },
                { title: '最差亏损', dataIndex: 'worstLossCents', width: 110, render: (v: number) => `¥${(v / 100).toLocaleString()}` },
                { title: '最差比分', dataIndex: 'worstScore', width: 80, render: (v?: string) => v || '—' },
                { title: '等级', dataIndex: 'riskLevel', width: 90, render: (v: string) => <LevelTag value={v} /> },
                {
                  title: '',
                  width: 72,
                  render: (_: unknown, r: RiskFixtureView) => (
                    <Button type="link" size="small" onClick={() => openFixture(r)}>
                      展开
                    </Button>
                  ),
                },
              ]}
            />
          </Card>
        </div>
      )}

      {level === 'sport' && (
        <div className="liability-pane">
          {!sportId ? (
            <Card className="content-card" title="选择球类">
              <Alert
                type="info"
                showIcon
                style={{ marginBottom: 16 }}
                message="球类限额与停盘需进入具体球类后配置"
                description="平台总体配置在「总体」页；选中球类后可编辑该球类限额，并批量停/开该球类全部赛事。"
              />
              <Row gutter={[12, 12]}>
                {sportStats.map((s) => (
                  <Col xs={24} md={12} key={s.id}>
                    <button
                      type="button"
                      className="liability-sport-tile"
                      onClick={() => setQuery({ level: 'sport', sport: s.id, filter: filterOver ? 'over' : undefined })}
                    >
                      <div className="liability-sport-tile-top">
                        <span className="liability-sport-tile-name">{s.emoji} {s.label}</span>
                        <RightOutlined />
                      </div>
                      <div className="liability-sport-tile-meta">
                        <span>{s.leagueCount} 联赛</span>
                        <span>{s.matchCount} 场</span>
                        <span>限额中 {s.limitModeCount}</span>
                      </div>
                    </button>
                  </Col>
                ))}
              </Row>
            </Card>
          ) : (
            <>
              <ScopeDutyConfigPanel
                mode="sport"
                label={`${sportMeta(sportId).label} · 球类`}
                sportCode={sportId}
                onSaved={refresh}
              />
              <Card
                className="content-card"
                title={`${sportMeta(sportId).label} · 联赛`}
                extra={(
                  <Button type="link" icon={<ArrowLeftOutlined />} onClick={() => setQuery({ level: 'sport', filter: filterOver ? 'over' : undefined })}>
                    全部球类
                  </Button>
                )}
              >
                {leagueStats.length === 0 ? (
                  <Empty description="该球类暂无联赛" />
                ) : (
                  <Table
                    size="middle"
                    rowKey={(r) => `${r.sport}:${r.id}`}
                    dataSource={leagueStats}
                    pagination={false}
                    onRow={(l) => ({
                      onClick: () => setQuery({
                        level: 'league',
                        sport: l.sport,
                        league: l.id,
                        filter: filterOver ? 'over' : undefined,
                      }),
                      style: { cursor: 'pointer' },
                    })}
                    columns={[
                      { title: '联赛', dataIndex: 'name' },
                      { title: '代码', dataIndex: 'id', width: 140 },
                      { title: '赛事', dataIndex: 'matchCount', width: 80 },
                      { title: '投注合计', dataIndex: 'totalStake', width: 120, render: (v: number) => v.toLocaleString() },
                      { title: '限额中', dataIndex: 'limitModeCount', width: 80 },
                    ]}
                  />
                )}
              </Card>
            </>
          )}
        </div>
      )}

      {level === 'league' && (
        <div className="liability-pane">
          {!leagueId ? (
            <Card className="content-card" title="全部联赛">
              <Alert
                type="info"
                showIcon
                style={{ marginBottom: 16 }}
                message="联赛限额与停盘需进入具体联赛后配置"
                description="选中联赛后可编辑该联赛限额，并批量停/开该联赛全部赛事。平台总体 / 球类配置在对应层级页。"
              />
              {leagueStats.length === 0 ? (
                <Empty description="暂无联赛" />
              ) : (
                <Table
                  size="small"
                  rowKey={(r) => `${r.sport}:${r.id}`}
                  dataSource={leagueStats}
                  pagination={false}
                  onRow={(l) => ({
                    onClick: () => setQuery({
                      level: 'league',
                      sport: l.sport,
                      league: l.id,
                      filter: filterOver ? 'over' : undefined,
                    }),
                    style: { cursor: 'pointer' },
                  })}
                  columns={[
                    { title: '球类', width: 90, render: (_: unknown, l) => sportMeta(l.sport).label },
                    { title: '联赛', dataIndex: 'name' },
                    { title: '代码', dataIndex: 'id', width: 120 },
                    { title: '赛事', dataIndex: 'matchCount', width: 70 },
                    { title: '投注合计', dataIndex: 'totalStake', width: 120, render: (v: number) => v.toLocaleString() },
                    { title: '限额中', dataIndex: 'limitModeCount', width: 80 },
                    {
                      title: '',
                      width: 100,
                      render: (_: unknown, l) => (
                        <Button type="link" size="small" onClick={(e) => {
                          e.stopPropagation();
                          setQuery({
                            level: 'league',
                            sport: l.sport,
                            league: l.id,
                            filter: filterOver ? 'over' : undefined,
                          });
                        }}
                        >
                          进入 <RightOutlined />
                        </Button>
                      ),
                    },
                  ]}
                />
              )}
            </Card>
          ) : (
            <>
              <ScopeDutyConfigPanel
                mode="league"
                label={`${currentLeagueName} · 联赛`}
                sportCode={sportId || 'football'}
                leagueCode={leagueId}
                onSaved={refresh}
              />
              <Card
                className="content-card liability-match-list-card"
                title={(
                  <div className="liability-list-title">
                    <span>{currentLeagueName} · 赛事</span>
                    <Typography.Text type="secondary" className="liability-list-hint">
                      {leagueMatches.length} 场 · 点行展开本场事实
                    </Typography.Text>
                  </div>
                )}
                extra={(
                  <Button
                    type="link"
                    icon={<ArrowLeftOutlined />}
                    onClick={() => setQuery({ level: 'league', sport: sportId, filter: filterOver ? 'over' : undefined })}
                  >
                    全部联赛
                  </Button>
                )}
              >
                {leagueMatches.length === 0 ? (
                  <Empty description="该联赛暂无赛事" />
                ) : (
                  <Table
                    className="liability-match-table"
                    size="middle"
                    rowKey="matchCode"
                    dataSource={leagueMatches}
                    pagination={false}
                    expandable={matchListExpandable}
                    onRow={(m) => ({
                      onClick: () => toggleMatchExpand(m),
                      style: { cursor: 'pointer' },
                    })}
                    columns={[
                      {
                        title: '对阵',
                        render: (_: unknown, m: SportsMatch) => (
                          <Space>
                            <span className="liability-match-name">{m.homeTeam} vs {m.awayTeam}</span>
                            <Tag color={m.status === 'SUSPENDED' ? 'red' : m.limitMode ? 'orange' : 'green'}>
                              {m.status === 'SUSPENDED' ? '已停盘' : m.limitMode ? '限额中' : '正常'}
                            </Tag>
                          </Space>
                        ),
                      },
                      { title: '代码', dataIndex: 'matchCode', width: 140 },
                      { title: '投注合计', width: 120, render: (_: unknown, m: SportsMatch) => Number(m.currentExposure).toLocaleString() },
                      { title: 'δ', width: 70, render: (_: unknown, m: SportsMatch) => Number(m.delta) },
                    ]}
                  />
                )}
              </Card>
            </>
          )}
        </div>
      )}

      {level === 'match' && (
        <div className="liability-pane">
          {!matchCode && (
            <Card
              className="content-card liability-match-list-card"
              title={(
                <div className="liability-list-title">
                  <span>{filterOver ? '超额赛事' : '赛事值班台'}</span>
                  <Typography.Text type="secondary" className="liability-list-hint">
                    {matchList.length} 场 · 点左侧箭头或整行展开 · 限额/停盘进值班页
                  </Typography.Text>
                </div>
              )}
            >
              {matchList.length === 0 ? (
                <Empty description="暂无赛事">
                  <Button type="primary" loading={demoLoading} onClick={() => loadDemoData(false)}>灌入演示数据</Button>
                </Empty>
              ) : (
                <Table
                  className="liability-match-table"
                  size="middle"
                  rowKey="matchCode"
                  dataSource={matchList}
                  pagination={{ pageSize: 20, showSizeChanger: false }}
                  expandable={matchListExpandable}
                  onRow={(m) => ({
                    onClick: () => toggleMatchExpand(m),
                    style: { cursor: 'pointer' },
                  })}
                  columns={[
                    { title: '球类', width: 72, render: (_: unknown, m: SportsMatch) => sportMeta(ofSport(m)).label },
                    { title: '联赛', width: 120, render: (_: unknown, m: SportsMatch) => leagueTitle(m) },
                    {
                      title: '对阵',
                      render: (_: unknown, m: SportsMatch) => (
                        <Space>
                          <span className="liability-match-name">{m.homeTeam} vs {m.awayTeam}</span>
                          <Tag color={m.status === 'SUSPENDED' ? 'red' : m.limitMode ? 'orange' : 'green'}>
                            {m.status === 'SUSPENDED' ? '已停盘' : m.limitMode ? '限额中' : '正常'}
                          </Tag>
                        </Space>
                      ),
                    },
                    { title: '投注合计', width: 110, render: (_: unknown, m: SportsMatch) => Number(m.currentExposure).toLocaleString() },
                    { title: '阈值', width: 100, render: (_: unknown, m: SportsMatch) => Number(m.exposureThreshold).toLocaleString() },
                  ]}
                />
              )}
            </Card>
          )}
          {matchCode && detailLoading && !detail && (
            <div style={{ textAlign: 'center', padding: 64 }}>
              <Spin size="large" tip="加载赛事…" />
            </div>
          )}
          {matchCode && detailError && !detail && (
            <Card className="content-card">
              <Empty description={detailError}>
                <Space>
                  <Button onClick={() => matchCode && openMatch(matchCode)}>重试</Button>
                  <Button type="primary" onClick={() => setQuery({ level: 'match', filter: filterOver ? 'over' : undefined })}>
                    返回赛事列表
                  </Button>
                </Space>
              </Empty>
            </Card>
          )}
          {matchCode && detail && (
            <Card
              className="content-card liability-duty-page"
              title={
                <Space wrap>
                  <span className="liability-match-name">{detail.homeTeam} vs {detail.awayTeam}</span>
                  <Tag color={detail.status === 'SUSPENDED' ? 'red' : detail.limitMode ? 'orange' : 'green'}>
                    {detail.status === 'SUSPENDED' ? '已停盘' : detail.limitMode ? '限额中' : '正常'}
                  </Tag>
                  {detail.overrideActive && <Tag color="blue">赛事覆盖</Tag>}
                  <Typography.Text type="secondary" style={{ fontSize: 13, fontWeight: 400 }}>
                    {detail.leagueName || currentLeagueName} · {detail.matchCode}
                  </Typography.Text>
                </Space>
              }
              extra={
                <Space wrap>
                  <Button
                    icon={<ArrowLeftOutlined />}
                    onClick={() => setQuery({
                      level: 'match',
                      sport: detail.sportCode || sportId,
                      league: detail.leagueCode || leagueId,
                      filter: filterOver ? 'over' : undefined,
                    })}
                  >
                    返回列表
                  </Button>
                  <Button
                    size="small"
                    danger={detail.status !== 'SUSPENDED'}
                    loading={statusSaving}
                    onClick={toggleSuspend}
                  >
                    {detail.status === 'SUSPENDED' ? '开盘' : '停盘'}
                  </Button>
                  <Button size="small" onClick={() => runCheck(detail.matchCode)}>刷新敞口</Button>
                  <Button
                    size="small"
                    type="primary"
                    disabled={detail.status === 'SUSPENDED'}
                    onClick={() => navigate(`/girisk/sandbox/bet?match=${detail.matchCode}`)}
                  >
                    投注试算
                  </Button>
                </Space>
              }
            >
              <DutyLimitsFold label="本场门控与限额（值班编辑）">
                <ScopeGateDutyBar
                  mode="match"
                  matchCode={detail.matchCode}
                  onSaved={() => { openMatch(detail.matchCode); refresh(); }}
                />
                <ScopeLimitDutyBar
                  mode="match"
                  matchCode={detail.matchCode}
                  title="赛事限额"
                  hint="本场最高优先级；未覆盖字段继承联赛→球类→默认。"
                  onSaved={() => { openMatch(detail.matchCode); refresh(); }}
                />
              </DutyLimitsFold>

              <div className="liability-section-head" style={{ marginTop: 8 }}>
                <Typography.Title level={5} style={{ margin: 0 }}>拦截结果汇总</Typography.Title>
                <Typography.Text type="secondary">Flink→Redis · 约 5s · 单笔见决策日志</Typography.Text>
              </div>
              <MatchInterceptSummary liveView={matchFixture} match={detail} />

              <Collapse
                className="liability-inline-markets"
                bordered={false}
                defaultActiveKey={['markets']}
                style={{ marginTop: 24 }}
                items={[{
                  key: 'markets',
                  label: (
                    <span>
                      各盘口明细（{detail.marketGroups.length}）
                      <Typography.Text type="secondary" style={{ marginLeft: 8, fontWeight: 400 }}>
                        Flink→Redis · 返彩口径 · 合计 {flinkMarketTotal.toLocaleString()}
                      </Typography.Text>
                    </span>
                  ),
                  children: detail.marketGroups.length === 0 ? (
                    <Empty description="等待 Flink 盘口视图（girisk:view:fixture.marketGroups）" />
                  ) : (
                    detail.marketGroups.map((g) => {
                      const mk = marketKeyOf(g);
                      const rows = rowsForGroup(g);
                      return (
                        <Card
                          key={mk}
                          size="small"
                          className="liability-market"
                          title={<span>{g.marketLabel}{g.line ? ` · ${g.line}` : ''}</span>}
                          style={{ marginBottom: 12 }}
                        >
                          <MarketGroupStatsStrip group={g} rows={rows} delta={Number(detail.delta)} />
                          <Table
                            size="small"
                            pagination={false}
                            rowKey="selection"
                            scroll={{ x: 560 }}
                            dataSource={rows}
                            rowClassName={(row) => (row.stake > row.maxAllowedAmount ? 'row-over-limit' : '')}
                            columns={outcomeLimitColumns({
                              line: g.line,
                              marketType: g.marketType,
                              editable: false,
                            })}
                          />
                        </Card>
                      );
                    })
                  ),
                }]}
              />
            </Card>
          )}
        </div>
      )}

      {/* unused filterOver list kept for 仅超额 on overall via over items if needed */}
      {level === 'overall' && filterOver && (dash?.overLimitOutcomes?.length ?? 0) > 0 && (
        <Card className="content-card" title="超额盘口" style={{ marginTop: 16 }}>
          <Table
            size="small"
            rowKey={(r: OverLimitOutcomeItem) => `${r.matchCode}-${r.marketLabel}-${r.line}-${r.selection}`}
            pagination={false}
            dataSource={dash?.overLimitOutcomes}
            columns={[
              {
                title: '比赛',
                render: (_: unknown, r: OverLimitOutcomeItem) => (
                  <Button type="link" style={{ padding: 0 }} onClick={() => {
                    const m = allMatches.find((x) => x.matchCode === r.matchCode);
                    if (m) enterMatch(m);
                    else setQuery({ level: 'match', match: r.matchCode, filter: 'over' });
                  }}>
                    {r.homeTeam} vs {r.awayTeam}
                  </Button>
                ),
              },
              { title: '玩法', dataIndex: 'marketLabel' },
              { title: '方向', render: (_: unknown, r: OverLimitOutcomeItem) => selectionLabel(r.selection, r.line, r.marketType) },
              { title: '已投', dataIndex: 'stake' },
              { title: '上限', dataIndex: 'maxAllowedAmount' },
            ]}
          />
        </Card>
      )}

    </div>
  );
}

function DutyLimitsFold({
  label,
  children,
  defaultOpen = false,
}: {
  label: string;
  children: ReactNode;
  defaultOpen?: boolean;
}) {
  return (
    <Collapse
      className="liability-duty-fold"
      bordered={false}
      defaultActiveKey={defaultOpen ? ['limits'] : []}
      items={[{
        key: 'limits',
        label: (
          <span className="liability-duty-fold-label">
            {label}
            <Typography.Text type="secondary"> · 按需展开编辑</Typography.Text>
          </span>
        ),
        children: <div className="liability-duty-fold-body">{children}</div>,
      }]}
    />
  );
}

/** 赛事列表行内展开：拦截汇总 + 盘口只读 */
function MatchInlinePanel({
  matchCode,
  cache,
  onRetry,
  onOpenDuty,
  onBetTrial,
}: {
  matchCode: string;
  cache?: MatchExpandCache[string];
  onRetry: () => void;
  onOpenDuty: () => void;
  onBetTrial: () => void;
}) {
  if (!cache || (cache.loading && !cache.view)) {
    return (
      <div className="liability-inline-loading">
        <Spin size="small" tip={`加载 ${matchCode}…`} />
      </div>
    );
  }
  if (cache.error && !cache.view) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={cache.error || '加载失败'}
        style={{ margin: '8px 0' }}
      >
        <Button size="small" onClick={onRetry}>重试</Button>
      </Empty>
    );
  }
  const view = cache.view;
  if (!view) {
    return (
      <div className="liability-inline-loading">
        <Spin size="small" tip={`加载 ${matchCode}…`} />
      </div>
    );
  }
  return (
    <div className="liability-inline-panel" onClick={(e) => e.stopPropagation()}>
      <div className="liability-inline-toolbar">
        <div>
          <div className="liability-match-name">{view.homeTeam} vs {view.awayTeam}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {view.leagueName || view.leagueCode} · {matchCode}
            {cache.loading ? ' · 刷新中…' : ''}
          </Typography.Text>
        </div>
        <Space wrap>
          <Button size="small" type="primary" disabled={view.status === 'SUSPENDED'} onClick={onBetTrial}>
            投注试算
          </Button>
          <Button size="small" onClick={onOpenDuty}>
            限额 / 停盘
          </Button>
        </Space>
      </div>

      <div className="liability-section-head">
        <Typography.Text strong>拦截结果汇总</Typography.Text>
        <Typography.Text type="secondary">Flink→Redis</Typography.Text>
      </div>
      <MatchInterceptSummary liveView={cache.fixture} match={view} />

      <Collapse
        className="liability-inline-markets"
        bordered={false}
        defaultActiveKey={['markets']}
        items={[{
          key: 'markets',
          label: `各盘口明细（${view.marketGroups.length}）· Flink→Redis`,
          children: view.marketGroups.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="等待 Flink 盘口视图" />
          ) : (
            view.marketGroups.map((g) => {
              const rows = g.outcomes?.length
                ? g.outcomes.map((o) => ({
                    selection: o.selection,
                    stake: Number(o.stake) || 0,
                    targetAmount: Number(o.targetAmount) || 0,
                    maxAllowedAmount: Number(o.maxAllowedAmount) || 0,
                    acceptMax: Number(o.acceptMax) || 0,
                  }))
                : buildOutcomeRows(
                    Object.keys(g.stakes || {}),
                    g.stakes || {},
                    Number(view.delta) || 0.2,
                    Number(view.seedPayoutYuan) || 0,
                  );
              return (
                <div key={`${g.marketType}|${g.line || ''}`} className="liability-inline-market">
                  <div className="liability-inline-market-title">
                    {g.marketLabel}{g.line ? ` · ${g.line}` : ''}
                  </div>
                  <MarketGroupStatsStrip group={g} rows={rows} delta={Number(view.delta) || 0.2} />
                  <Table
                    size="small"
                    pagination={false}
                    rowKey="selection"
                    scroll={{ x: 480 }}
                    dataSource={rows}
                    rowClassName={(row) => (row.stake > row.maxAllowedAmount ? 'row-over-limit' : '')}
                    columns={outcomeLimitColumns({
                      line: g.line,
                      marketType: g.marketType,
                      editable: false,
                    })}
                  />
                </div>
              );
            })
          ),
        }]}
      />
    </div>
  );
}

function fmtNum(n?: number | null, digits = 2) {
  if (n == null || Number.isNaN(n)) return '—';
  return n.toLocaleString(undefined, { maximumFractionDigits: digits, minimumFractionDigits: 0 });
}

function StatTile({
  label,
  value,
  tone,
}: {
  label: string;
  value: ReactNode;
  tone?: 'danger' | 'ok' | 'muted';
}) {
  const color = tone === 'danger' ? '#cf1322' : tone === 'ok' ? '#389e0d' : undefined;
  return (
    <Col xs={12} sm={8} md={6} lg={4}>
      <div
        style={{
          background: '#fafafa',
          border: '1px solid #f0f0f0',
          borderRadius: 8,
          padding: '12px 14px',
          minHeight: 78,
        }}
      >
        <Typography.Text type="secondary" style={{ fontSize: 12, display: 'block', marginBottom: 6 }}>
          {label}
        </Typography.Text>
        <div style={{ fontSize: 18, fontWeight: 600, color, wordBreak: 'break-all' }}>{value}</div>
      </div>
    </Col>
  );
}

/**
 * 赛事层「拦截结果汇总」：固定 12 格产品指标（与离线回放同一套）。
 * 优先 Redis replayStats；缺省时用实时窗口 + 场次限额参数补齐。
 */
function MatchInterceptSummary({
  liveView,
  match,
}: {
  liveView?: RiskFixtureView | null;
  match?: SportsMatchView | null;
}) {
  if (!liveView && !match) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="暂无本场数据（确认 Flink 已写入 Redis 视图，且 URL match=fixtureId）"
        style={{ margin: '8px 0 16px' }}
      />
    );
  }

  const rs = mergeInterceptStats(liveView, match);
  const riskLine =
    rs.maxWorstLossYuan != null
      ? `${fmtNum(rs.maxWorstLossYuan, 0)} / -${fmtNum(rs.maxWorstLossYuan, 0)}`
      : '—';

  return (
    <div>
      <Row gutter={[12, 12]}>
        <StatTile label="有效订单数" value={fmtNum(rs.totalOrders, 0)} />
        <StatTile label="接收订单数" value={fmtNum(rs.acceptedCount, 0)} tone="ok" />
        <StatTile label="拦截订单数" value={fmtNum(rs.rejectedTotal, 0)} tone="danger" />
        <StatTile label="重复订单数" value={fmtNum(rs.duplicateCount, 0)} tone="muted" />
        <StatTile label="限额拦截数" value={fmtNum(rs.rejectedLimit, 0)} tone="danger" />
        <StatTile label="风险拦截数" value={fmtNum(rs.rejectedExposure, 0)} tone="danger" />
        <StatTile label="初始已投注金额/盘口" value={fmtNum(rs.seedPayoutYuan, 0)} />
        <StatTile label="风险阈值 / 盈亏线" value={riskLine} />
        <StatTile label="完全不拦截最差盈亏" value={fmtNum(rs.noRiskWorstPnlYuan)} tone="danger" />
        <StatTile label="完全不拦截最差比分" value={rs.noRiskWorstScore || '—'} />
        <StatTile label="拦截后最差盈亏" value={fmtNum(rs.withRiskWorstPnlYuan)} tone="danger" />
        <StatTile label="拦截后最差比分" value={rs.withRiskWorstScore || '—'} />
        <StatTile label="拦截后累计投注金额" value={fmtNum(rs.acceptedStakeYuan)} />
      </Row>
      <Typography.Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0, fontSize: 13 }}>
        有效 = 接收 + 拦截 + 重复：{fmtNum(rs.totalOrders, 0)} = {fmtNum(rs.acceptedCount, 0)} +{' '}
        {fmtNum(rs.rejectedTotal, 0)} + {fmtNum(rs.duplicateCount, 0)}
        {' · '}限额拦截: {fmtNum(rs.rejectedLimit, 0)}条, 风险拦截: {fmtNum(rs.rejectedExposure, 0)}条
        {rs.delta != null ? ` · δ=${rs.delta}` : ''}
        {liveView?.updatedAt ? ` · Redis ${liveView.updatedAt}` : ''}
        。重复单 decision 仍为 PASS，不进接单窗口；「完全不拦截」含已见订单对照网格。
      </Typography.Paragraph>
    </div>
  );
}

function mergeInterceptStats(
  liveView?: RiskFixtureView | null,
  match?: SportsMatchView | null,
): FixtureReplayStats {
  const rs = liveView?.replayStats ?? {};
  const accepted = rs.acceptedCount ?? liveView?.confirmedOrders ?? 0;
  const rejected = rs.rejectedTotal ?? 0;
  const duplicate = rs.duplicateCount ?? 0;
  const rejectedLimit = rs.rejectedLimit ?? 0;
  const rejectedExposure = rs.rejectedExposure ?? 0;
  const worstYuanAbs = (liveView?.worstLossCents || 0) / 100;
  const withRiskPnl =
    rs.withRiskWorstPnlYuan ?? (liveView != null ? -Math.abs(worstYuanAbs) : undefined);
  const withRiskScore = rs.withRiskWorstScore || liveView?.worstScore || undefined;
  const seed = rs.seedPayoutYuan ?? match?.seedPayoutYuan;
  const maxWorst = rs.maxWorstLossYuan ?? match?.maxWorstLossYuan ?? match?.exposureThreshold;
  const delta = rs.delta ?? (match?.delta != null ? Number(match.delta) : undefined);
  // 展示恒等式：有效 = 接收 + 拦截 + 重复（不以漂移的 Redis totalOrders 为准）
  const accounted = accepted + rejected + duplicate;

  return {
    totalOrders: accounted,
    acceptedCount: accepted,
    rejectedTotal: rejected,
    duplicateCount: duplicate,
    rejectedLimit,
    rejectedExposure,
    seedPayoutYuan: seed,
    maxWorstLossYuan: maxWorst,
    delta,
    noRiskWorstPnlYuan: rs.noRiskWorstPnlYuan,
    noRiskWorstScore: rs.noRiskWorstScore,
    withRiskWorstPnlYuan: withRiskPnl,
    withRiskWorstScore: withRiskScore,
    acceptedStakeYuan: rs.acceptedStakeYuan,
  };
}

/** 盘口层：当前盘口汇总，放在方向明细表之上 */
function MarketGroupStatsStrip({
  rows,
  delta,
}: {
  group: MarketGroupView;
  rows: OutcomeLimitRow[];
  delta: number;
}) {
  const totalStake = groupStakeTotal(
    Object.fromEntries(rows.map((r) => [r.selection, r.stake])) as Record<string, number>,
  );
  const overCount = rows.filter((r) => r.stake > r.maxAllowedAmount).length;
  const acceptLeft = rows.reduce((s, r) => s + Math.max(0, r.acceptMax ?? 0), 0);
  return (
    <Row gutter={16} style={{ marginBottom: 12 }}>
      <Col span={6}>
        <Typography.Text type="secondary">盘口累计投注</Typography.Text>
        <div style={{ fontWeight: 600 }}>{fmtNum(totalStake)}</div>
      </Col>
      <Col span={6}>
        <Typography.Text type="secondary">超额方向</Typography.Text>
        <div style={{ fontWeight: 600, color: overCount ? '#cf1322' : undefined }}>
          {overCount} / {rows.length}
        </div>
      </Col>
      <Col span={6}>
        <Typography.Text type="secondary">还能接收合计</Typography.Text>
        <div style={{ fontWeight: 600 }}>{fmtNum(acceptLeft)}</div>
      </Col>
      <Col span={6}>
        <Typography.Text type="secondary">等比例 δ</Typography.Text>
        <div style={{ fontWeight: 600 }}>{delta}</div>
      </Col>
    </Row>
  );
}
