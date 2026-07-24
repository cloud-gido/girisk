import {
  AlertOutlined,
  AppstoreOutlined,
  DollarOutlined,
  DownOutlined,
  TrophyOutlined,
} from '@ant-design/icons';
import {
  Button,
  Card,
  Col,
  Empty,
  Input,
  Row,
  Segmented,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import MatchDutyDrawer, { GateDots } from '../components/MatchDutyDrawer';
import {
  MatchInterceptSummary,
  MatchMarketsCollapse,
} from '../components/MatchDutyPanels';
import ScopeDutyConfigPanel from '../components/ScopeDutyConfigPanel';
import { sportsApi } from '../api/sportsClient';
import { riskApi } from '../api/riskClient';
import type {
  RiskFixtureView,
  SportsDashboardSummary,
  SportsMatch,
  SportsMatchListRow,
  SportsMatchView,
} from '../types';
import { blankLabel, matchupLabel, sportMeta } from '../utils/matchDisplay';
import { resolveMatchFromFixture } from '../utils/exposureNav';
import {
  readExposureNav,
  writeExposureNav,
  type ExposureLevel,
} from '../utils/exposureNavStorage';
import { LevelTag } from '../utils/tags';

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

function ofSport(m: Pick<SportsMatch, 'sportCode'>) {
  return m.sportCode || 'football';
}

function ofLeague(m: Pick<SportsMatch, 'leagueCode'>) {
  return m.leagueCode || 'UNKNOWN';
}

function leagueTitle(m: Pick<SportsMatch, 'leagueName' | 'leagueCode'>) {
  return blankLabel(m.leagueName || m.leagueCode, '未分组');
}

function utilization(row: SportsMatchListRow): number | null {
  const thr = Number(row.maxWorstLossYuan ?? row.exposureThreshold) || 0;
  const loss = row.worstLossCents != null
    ? Math.abs(row.worstLossCents) / 100
    : Number(row.currentExposure) || 0;
  if (thr <= 0) return null;
  return Math.round((loss / thr) * 1000) / 10;
}

export default function LiabilityBoardPage() {
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const sportId = params.get('sport') || undefined;
  const leagueId = params.get('league') || undefined;
  const matchCode = params.get('match') || undefined;
  const filterOver = params.get('filter') === 'over';
  const qParam = params.get('q') || '';
  const statusParam = params.get('status') || '';
  const gateParam = params.get('gate') || '';
  const levelParam = params.get('level') as Level | null;

  const level: Level = levelParam
    || (matchCode ? 'match' : leagueId ? 'league' : sportId ? 'sport' : 'match');

  const [dash, setDash] = useState<SportsDashboardSummary | null>(null);
  const [rows, setRows] = useState<SportsMatchListRow[]>([]);
  const [fixtures, setFixtures] = useState<RiskFixtureView[]>([]);
  const [loading, setLoading] = useState(true);
  const [demoLoading, setDemoLoading] = useState(false);
  const [hydrated, setHydrated] = useState(false);
  const [drawerCode, setDrawerCode] = useState<string | null>(null);
  const [qDraft, setQDraft] = useState(qParam);
  const [expandedMatchCodes, setExpandedMatchCodes] = useState<string[]>([]);
  const [expandCache, setExpandCache] = useState<MatchExpandCache>({});

  const setQuery = useCallback((next: Record<string, string | undefined>) => {
    const sp = new URLSearchParams();
    Object.entries(next).forEach(([k, v]) => {
      if (v) sp.set(k, v);
    });
    setParams(sp, { replace: false });
    writeExposureNav({
      level: (next.level as Level) || 'match',
      sport: next.sport,
      league: next.league,
      match: next.match,
      filter: next.filter,
    });
  }, [setParams]);

  const listQuery = useMemo(() => ({
    sportCode: level === 'sport' || level === 'league' || (level === 'match' && sportId)
      ? sportId
      : undefined,
    leagueCode: level === 'league' || (level === 'match' && leagueId) ? leagueId : undefined,
    q: qParam || undefined,
    status: statusParam || undefined,
    limitMode: filterOver ? true : undefined,
    gateOff: gateParam || undefined,
  }), [level, sportId, leagueId, qParam, statusParam, filterOver, gateParam]);

  const refresh = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const [d, list, fx] = await Promise.all([
        sportsApi.dashboard(),
        sportsApi.matches(listQuery),
        riskApi.fixturesTop(8).catch(() => [] as RiskFixtureView[]),
      ]);
      setDash(d);
      setRows(list);
      setFixtures(fx);
    } catch (e) {
      if (!silent) message.error((e as Error).message);
    } finally {
      if (!silent) setLoading(false);
    }
  }, [listQuery]);

  useEffect(() => { void refresh(false); }, [refresh]);

  useEffect(() => {
    const id = window.setInterval(() => {
      if (document.visibilityState === 'hidden') return;
      void refresh(true);
    }, 5_000);
    return () => window.clearInterval(id);
  }, [refresh]);

  useEffect(() => {
    setQDraft(qParam);
  }, [qParam]);

  useEffect(() => {
    if (hydrated) return;
    const hasUrl = !!(levelParam || sportId || leagueId || matchCode || filterOver || qParam);
    if (!hasUrl) {
      const saved = readExposureNav();
      if (saved?.level) {
        setQuery({
          level: saved.level === 'overall' ? 'overall' : 'match',
          sport: saved.sport,
          league: saved.league,
          filter: saved.filter,
        });
      } else {
        setQuery({ level: 'match' });
      }
    }
    setHydrated(true);
  }, [hydrated, levelParam, sportId, leagueId, matchCode, filterOver, qParam, setQuery]);

  useEffect(() => {
    if (matchCode && hydrated) {
      setDrawerCode(matchCode);
    }
  }, [matchCode, hydrated]);

  const allMatches = dash?.matches ?? [];
  /** 与下方工作台同一套筛选；有筛选时 KPI 跟列表对齐，避免「在管 13 / 表 0」。 */
  const listFiltersActive = !!(
    filterOver || sportId || leagueId || qParam || statusParam || gateParam
  );
  const kpi = useMemo(() => {
    if (!listFiltersActive) {
      return {
        matchCount: dash?.matchCount ?? 0,
        outcomeCount: dash?.outcomeCount ?? 0,
        overLimitOutcomeCount: dash?.overLimitOutcomeCount ?? 0,
        totalStake: Number(dash?.totalStake ?? 0),
        limitModeMatchCount: dash?.limitModeMatchCount ?? 0,
        suspendedCount: allMatches.filter((m) => m.status === 'SUSPENDED').length,
        scoped: false as const,
      };
    }
    return {
      matchCount: rows.length,
      // 盘口明细不在列表行上：空列表时归零；有行时沿用全平台盘口数并标「筛选中」
      outcomeCount: rows.length === 0 ? 0 : (dash?.outcomeCount ?? 0),
      overLimitOutcomeCount: rows.length === 0 ? 0 : (dash?.overLimitOutcomeCount ?? 0),
      totalStake: rows.reduce((s, r) => s + Number(r.currentExposure || 0), 0),
      limitModeMatchCount: rows.filter((r) => r.limitMode).length,
      suspendedCount: rows.filter((r) => r.status === 'SUSPENDED').length,
      scoped: true as const,
    };
  }, [listFiltersActive, dash, allMatches, rows]);

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

  const sportOptions = useMemo(
    () => sportStats.map((s) => ({ value: s.id, label: `${s.emoji} ${s.label}` })),
    [sportStats],
  );

  const leagueOptions = useMemo(() => {
    const src = sportId
      ? leagueStats.filter((l) => l.sport === sportId)
      : leagueStats;
    return src.map((l) => ({
      value: l.id,
      label: `${l.name} (${l.sport})`,
    }));
  }, [leagueStats, sportId]);

  const pickDefaultLeague = useCallback(
    (sport: string, prefer?: string) => {
      const list = allMatches.filter((m) => ofSport(m) === sport);
      const codes = Array.from(new Set(list.map(ofLeague)));
      if (prefer && codes.includes(prefer)) return prefer;
      return codes[0] || 'UNKNOWN';
    },
    [allMatches],
  );

  const goLevel = (v: Level) => {
    const f = filterOver ? 'over' : undefined;
    const saved = readExposureNav();
    if (v === 'overall') {
      setQuery({ level: 'overall', filter: f, q: qParam || undefined });
      return;
    }
    if (v === 'sport') {
      setQuery({
        level: 'sport',
        sport: sportId || saved?.sport || 'football',
        filter: f,
        q: qParam || undefined,
      });
      return;
    }
    if (v === 'league') {
      const sport = sportId || saved?.sport || 'football';
      const league = pickDefaultLeague(sport, leagueId || saved?.league);
      setQuery({
        level: 'league',
        sport,
        league,
        filter: f,
        q: qParam || undefined,
      });
      return;
    }
    setQuery({
      level: 'match',
      sport: sportId,
      league: leagueId,
      filter: f,
      q: qParam || undefined,
      status: statusParam || undefined,
      gate: gateParam || undefined,
    });
  };

  const applyFilters = (patch: Record<string, string | undefined>) => {
    const pick = (key: string, current: string | undefined) =>
      Object.prototype.hasOwnProperty.call(patch, key) ? patch[key] : current;
    setQuery({
      level: 'match',
      sport: pick('sport', sportId),
      league: pick('league', leagueId),
      filter: pick('filter', filterOver ? 'over' : undefined),
      q: pick('q', qParam || undefined),
      status: pick('status', statusParam || undefined),
      gate: pick('gate', gateParam || undefined),
    });
  };

  /** KPI 下转到赛事工作台（保留球类/联赛上下文，覆盖状态类筛选）。 */
  const drillToMatch = (patch: {
    filter?: string;
    status?: string;
    clearScope?: boolean;
  } = {}) => {
    setQuery({
      level: 'match',
      sport: patch.clearScope ? undefined : sportId,
      league: patch.clearScope ? undefined : leagueId,
      filter: patch.filter,
      status: patch.status,
      q: undefined,
      gate: undefined,
    });
  };

  // 联赛页：球类切换后若当前联赛不在列表内，自动落到首个（或 UNKNOWN 模板）
  useEffect(() => {
    if (level !== 'league' || !sportId || !hydrated) return;
    const valid = leagueStats.some((l) => l.sport === sportId && l.id === leagueId);
    if (valid) return;
    const next = pickDefaultLeague(sportId);
    if (next !== leagueId) {
      setQuery({
        level: 'league',
        sport: sportId,
        league: next,
        filter: filterOver ? 'over' : undefined,
      });
    }
  }, [level, sportId, leagueId, leagueStats, hydrated, pickDefaultLeague, filterOver, setQuery]);

  const openConfig = (code: string) => {
    setDrawerCode(code);
    setQuery({
      level: 'match',
      sport: sportId,
      league: leagueId,
      match: code,
      filter: filterOver ? 'over' : undefined,
      q: qParam || undefined,
      status: statusParam || undefined,
      gate: gateParam || undefined,
    });
  };

  const closeDrawer = () => {
    setDrawerCode(null);
    setQuery({
      level: 'match',
      sport: sportId,
      league: leagueId,
      filter: filterOver ? 'over' : undefined,
      q: qParam || undefined,
      status: statusParam || undefined,
      gate: gateParam || undefined,
    });
  };

  const toggleRowStatus = async (row: SportsMatchListRow) => {
    const next = row.status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED';
    try {
      await sportsApi.setMatchStatus(row.matchCode, next);
      message.success(next === 'SUSPENDED' ? '已停盘' : '已开盘');
      void refresh(true);
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  const loadDemoData = async () => {
    setDemoLoading(true);
    try {
      await sportsApi.loadDemoReplay(true);
      message.success('演示数据已灌入');
      await refresh();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setDemoLoading(false);
    }
  };

  const openFixture = (r: RiskFixtureView) => {
    const m = resolveMatchFromFixture(r, allMatches);
    const code = m?.matchCode || r.fixtureId;
    applyFilters({ q: code, sport: m ? ofSport(m) : undefined, league: m ? ofLeague(m) : undefined });
    setExpandedMatchCodes([code]);
    void loadMatchExpand(code);
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

  const matchListExpandable = useMemo((): TableProps<SportsMatchListRow>['expandable'] => ({
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
        aria-label={expanded ? '收起监控' : '展开监控'}
        icon={<DownOutlined rotate={expanded ? 180 : 0} style={{ transition: 'transform .2s' }} />}
        onClick={(e) => {
          e.stopPropagation();
          onExpand(record, e);
        }}
      />
    ),
    expandedRowRender: (m) => (
      <MatchInlineMonitor
        matchCode={m.matchCode}
        cache={expandCache[m.matchCode]}
        onRetry={() => void loadMatchExpand(m.matchCode, true)}
        onOpenConfig={() => openConfig(m.matchCode)}
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

  const columns = [
    {
      title: '赛事 ID',
      dataIndex: 'matchCode',
      width: 140,
      render: (v: string) => (
        <Typography.Text copyable={{ text: v }} style={{ fontFamily: 'monospace', fontSize: 12 }}>
          {v}
        </Typography.Text>
      ),
    },
    {
      title: '球类',
      width: 72,
      render: (_: unknown, r: SportsMatchListRow) => sportMeta(r.sportCode).label,
    },
    {
      title: '联赛',
      width: 120,
      ellipsis: true,
      render: (_: unknown, r: SportsMatchListRow) => blankLabel(r.leagueName || r.leagueCode),
    },
    {
      title: '对阵',
      ellipsis: true,
      render: (_: unknown, r: SportsMatchListRow) => matchupLabel(r.homeTeam, r.awayTeam, r.matchCode),
    },
    {
      title: '比分',
      width: 88,
      render: (_: unknown, r: SportsMatchListRow) => blankLabel(r.liveScore || r.worstScore),
    },
    {
      title: '投注额',
      width: 96,
      align: 'right' as const,
      render: (_: unknown, r: SportsMatchListRow) => Number(r.currentExposure || 0).toLocaleString(),
    },
    {
      title: '最差亏损',
      width: 100,
      align: 'right' as const,
      render: (_: unknown, r: SportsMatchListRow) =>
        r.worstLossCents != null
          ? `¥${(Math.abs(r.worstLossCents) / 100).toLocaleString()}`
          : '—',
    },
    {
      title: '阈值',
      width: 88,
      align: 'right' as const,
      render: (_: unknown, r: SportsMatchListRow) =>
        Number(r.maxWorstLossYuan ?? r.exposureThreshold ?? 0).toLocaleString(),
    },
    {
      title: '占用%',
      width: 72,
      align: 'right' as const,
      render: (_: unknown, r: SportsMatchListRow) => {
        const u = utilization(r);
        if (u == null) return '—';
        return (
          <span style={{ color: u >= 100 || r.limitMode ? '#cf1322' : undefined }}>
            {u}%
          </span>
        );
      },
    },
    {
      title: '门控',
      width: 100,
      render: (_: unknown, r: SportsMatchListRow) => (
        <Tooltip title={<pre style={{ margin: 0, fontSize: 11 }}>{[
          `开盘 ${r.tradingEnabled ? '开' : '关'} · ${r.tradingSource}`,
          `限额 ${r.limitGateEnabled ? '开' : '关'} · ${r.limitGateSource}`,
          `敞口 ${r.exposureGateEnabled ? '开' : '关'} · ${r.exposureGateSource}`,
        ].join('\n')}</pre>}>
          <span><GateDots row={r} /></span>
        </Tooltip>
      ),
    },
    {
      title: 'δ',
      width: 56,
      render: (_: unknown, r: SportsMatchListRow) => Number(r.delta ?? 0).toFixed(2),
    },
    {
      title: '状态',
      width: 80,
      render: (_: unknown, r: SportsMatchListRow) => (
        <Space size={4} direction="vertical">
          <Tag color={r.status === 'SUSPENDED' ? 'default' : 'green'}>
            {r.status === 'SUSPENDED' ? '停盘' : '开盘'}
          </Tag>
          {r.limitMode ? <Tag color="error">超额</Tag> : null}
          {r.riskLevel ? <LevelTag value={r.riskLevel} /> : null}
        </Space>
      ),
    },
    {
      title: '操作',
      width: 140,
      fixed: 'right' as const,
      render: (_: unknown, r: SportsMatchListRow) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => openConfig(r.matchCode)}>配置</Button>
          <Button type="link" size="small" onClick={() => void toggleRowStatus(r)}>
            {r.status === 'SUSPENDED' ? '开盘' : '停盘'}
          </Button>
        </Space>
      ),
    },
  ];

  if (loading && !dash) {
    return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />;
  }

  return (
    <div className="liability-board">
      <header className="liability-chrome">
        <div className="liability-chrome-main">
          <div className="liability-chrome-title">
            <h2>敞口看板</h2>
            <p>
              总览 KPI → 范围门控/限额 → 全量赛事工作台。赛事以 ID 为唯一键；元数据可留白，运营在配置抽屉补全。
            </p>
          </div>
          <Space wrap className="liability-chrome-actions">
            <Button size="small" loading={demoLoading} onClick={() => void loadDemoData()}>重灌演示</Button>
            <Button size="small" type="primary" ghost onClick={() => { void refresh(false); }}>刷新</Button>
          </Space>
        </div>
        <div className="liability-chrome-nav">
          <Segmented
            value={level === 'match' && !matchCode ? 'match' : level}
            onChange={(v) => goLevel(v as Level)}
            options={[
              { label: '总体配置', value: 'overall' },
              { label: '球类', value: 'sport' },
              { label: '联赛', value: 'league' },
              { label: '赛事工作台', value: 'match' },
            ]}
          />
        </div>
      </header>

      {/* KPI：点击下转到工作台；有筛选时与列表同源 */}
      <Row gutter={[12, 12]} style={{ marginBottom: 8 }}>
        <Col xs={12} sm={8} md={4}>
          <Card
            size="small"
            className="content-card"
            hoverable
            onClick={() => drillToMatch({ clearScope: kpi.scoped })}
          >
            <StatisticLike
              icon={<TrophyOutlined />}
              label={kpi.scoped ? '在管赛事（当前筛选）' : '在管赛事'}
              value={kpi.matchCount}
              hint="下转工作台"
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card
            size="small"
            className="content-card"
            hoverable
            onClick={() => drillToMatch({})}
          >
            <StatisticLike
              icon={<AppstoreOutlined />}
              label="盘口数"
              value={kpi.outcomeCount}
              hint="下转工作台"
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card
            size="small"
            className="content-card"
            hoverable
            onClick={() => drillToMatch({ filter: 'over' })}
          >
            <StatisticLike
              icon={<AlertOutlined />}
              label="超额盘口"
              value={kpi.overLimitOutcomeCount}
              danger={kpi.overLimitOutcomeCount > 0}
              hint="仅超额赛事"
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card
            size="small"
            className="content-card"
            hoverable
            onClick={() => drillToMatch({})}
          >
            <StatisticLike
              icon={<DollarOutlined />}
              label="总投注"
              value={kpi.totalStake.toLocaleString()}
              hint="下转工作台"
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card
            size="small"
            className="content-card"
            hoverable
            onClick={() => drillToMatch({ filter: 'over' })}
          >
            <StatisticLike
              label="超额赛事"
              value={kpi.limitModeMatchCount}
              danger={kpi.limitModeMatchCount > 0}
              hint="仅超额赛事"
            />
          </Card>
        </Col>
        <Col xs={12} sm={8} md={4}>
          <Card
            size="small"
            className="content-card"
            hoverable
            onClick={() => drillToMatch({ status: 'SUSPENDED' })}
          >
            <StatisticLike label="停盘" value={kpi.suspendedCount} hint="仅停盘" />
          </Card>
        </Col>
      </Row>
      {kpi.scoped && (
        <Typography.Paragraph type="secondary" style={{ marginBottom: 16, fontSize: 12 }}>
          当前筛选与下方列表一致
          {filterOver ? '（仅超额）' : ''}
          {statusParam === 'SUSPENDED' ? '（仅停盘）' : ''}
          {kpi.matchCount === 0 ? ' · 无匹配场次，点「在管赛事」看全量' : ''}
          。
        </Typography.Paragraph>
      )}

      {/* 范围配置：总体 / 球类 / 联赛 */}
      {level === 'overall' && (
        <Card className="content-card" title="总体门控与限额" style={{ marginBottom: 16 }}>
          <ScopeDutyConfigPanel
            mode="overall"
            label="平台总体"
            defaultOpen
            onSaved={() => void refresh(true)}
          />
          {fixtures.length > 0 && (
            <>
              <Typography.Title level={5} style={{ marginTop: 24 }}>高危场次（Redis）</Typography.Title>
              <Table
                size="small"
                rowKey="fixtureId"
                pagination={false}
                dataSource={fixtures}
                columns={[
                  {
                    title: '场次',
                    render: (_: unknown, r: RiskFixtureView) => (
                      <Button type="link" style={{ padding: 0 }} onClick={() => openFixture(r)}>
                        {matchupLabel(r.homeTeam, r.awayTeam, r.fixtureId)}
                      </Button>
                    ),
                  },
                  {
                    title: '最差亏损',
                    dataIndex: 'worstLossCents',
                    render: (v: number) => `¥${(v / 100).toLocaleString()}`,
                  },
                  { title: '等级', dataIndex: 'riskLevel', render: (v: string) => <LevelTag value={v} /> },
                ]}
              />
            </>
          )}
        </Card>
      )}

      {level === 'sport' && (
        <Card className="content-card" title="球类范围" style={{ marginBottom: 16 }}>
          <Space wrap style={{ marginBottom: 16 }}>
            {sportStats.map((s) => (
              <Button
                key={s.id}
                type={sportId === s.id ? 'primary' : 'default'}
                onClick={() => setQuery({
                  level: 'sport',
                  sport: s.id,
                  filter: filterOver ? 'over' : undefined,
                })}
              >
                {s.emoji} {s.label} · {s.matchCount} 场
              </Button>
            ))}
          </Space>
          {sportId && (
            <ScopeDutyConfigPanel
              mode="sport"
              sportCode={sportId}
              label={`${sportMeta(sportId).label} · 球类`}
              defaultOpen
              onSaved={() => void refresh(true)}
            />
          )}
        </Card>
      )}

      {level === 'league' && (
        <Card className="content-card" title="联赛范围" style={{ marginBottom: 16 }}>
          <Space wrap style={{ marginBottom: 12 }}>
            <Select
              style={{ width: 160 }}
              placeholder="球类"
              value={sportId}
              options={sportOptions}
              onChange={(v) => {
                const league = pickDefaultLeague(v);
                setQuery({
                  level: 'league',
                  sport: v,
                  league,
                  filter: filterOver ? 'over' : undefined,
                });
              }}
            />
          </Space>
          <Space wrap style={{ marginBottom: 16 }}>
            {(sportId ? leagueStats.filter((l) => l.sport === sportId) : leagueStats).map((l) => (
              <Button
                key={`${l.sport}:${l.id}`}
                type={leagueId === l.id ? 'primary' : 'default'}
                onClick={() => setQuery({
                  level: 'league',
                  sport: l.sport,
                  league: l.id,
                  filter: filterOver ? 'over' : undefined,
                })}
              >
                {l.name} · {l.matchCount} 场
              </Button>
            ))}
            {sportId && !leagueStats.some((l) => l.sport === sportId) && (
              <Button
                type={leagueId === 'UNKNOWN' ? 'primary' : 'default'}
                onClick={() => setQuery({
                  level: 'league',
                  sport: sportId,
                  league: 'UNKNOWN',
                  filter: filterOver ? 'over' : undefined,
                })}
              >
                未分组联赛 · 0 场
              </Button>
            )}
          </Space>
          {sportId && !leagueStats.some((l) => l.sport === sportId) && (
            <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
              该球类暂无赛事入库；仍可配置「未分组」联赛层门控/限额模板，有空壳同步后会挂到此处。
            </Typography.Paragraph>
          )}
          {sportId && (
            <ScopeDutyConfigPanel
              mode="league"
              sportCode={sportId}
              leagueCode={leagueId || 'UNKNOWN'}
              label={`${leagueTitle({
                leagueCode: leagueId || 'UNKNOWN',
                leagueName: leagueStats.find((l) => l.id === (leagueId || 'UNKNOWN'))?.name,
              })} · 联赛`}
              defaultOpen
              onSaved={() => void refresh(true)}
            />
          )}
        </Card>
      )}

      {/* 赛事工作台：筛选 + 全量表（主路径，总体/球类/联赛下也展示缩小版时可切到 match） */}
      {(level === 'match' || level === 'overall') && (
        <Card
          className="content-card"
          title={
            <Space>
              <span>赛事工作台</span>
              <Typography.Text type="secondary" style={{ fontWeight: 400, fontSize: 13 }}>
                {rows.length} 场 · 行首箭头展开拦截/盘口监控 · 「配置」改元数据与门控限额
              </Typography.Text>
            </Space>
          }
        >
          <Space wrap style={{ marginBottom: 12 }} size={[8, 8]}>
            <Input.Search
              allowClear
              placeholder="赛事 ID / 对阵"
              style={{ width: 220 }}
              value={qDraft}
              onChange={(e) => setQDraft(e.target.value)}
              onSearch={(v) => applyFilters({ q: v || undefined })}
            />
            <Select
              allowClear
              placeholder="球类"
              style={{ width: 120 }}
              value={sportId}
              options={sportOptions}
              onChange={(v) => applyFilters({ sport: v, league: undefined })}
            />
            <Select
              allowClear
              showSearch
              optionFilterProp="label"
              placeholder="联赛"
              style={{ width: 180 }}
              value={leagueId}
              options={leagueOptions}
              onChange={(v) => applyFilters({ league: v })}
            />
            <Select
              allowClear
              placeholder="状态"
              style={{ width: 110 }}
              value={statusParam || undefined}
              options={[
                { value: 'ACTIVE', label: '开盘' },
                { value: 'SUSPENDED', label: '停盘' },
              ]}
              onChange={(v) => applyFilters({ status: v })}
            />
            <Select
              allowClear
              placeholder="门控关闭"
              style={{ width: 130 }}
              value={gateParam || undefined}
              options={[
                { value: 'trading', label: '交易关' },
                { value: 'limit', label: '限额关' },
                { value: 'exposure', label: '敞口关' },
              ]}
              onChange={(v) => applyFilters({ gate: v })}
            />
            <Segmented
              size="small"
              value={filterOver ? 'over' : 'all'}
              onChange={(v) => applyFilters({ filter: v === 'over' ? 'over' : undefined })}
              options={[
                { label: '全部', value: 'all' },
                { label: '仅超额', value: 'over' },
              ]}
            />
          </Space>

          <Table
            size="small"
            rowKey="matchCode"
            loading={loading}
            dataSource={rows}
            columns={columns}
            expandable={matchListExpandable}
            scroll={{ x: 1280 }}
            pagination={{ pageSize: 50, showSizeChanger: true }}
            rowClassName={(r) => (r.limitMode ? 'row-over-limit' : '')}
          />
        </Card>
      )}

      {level === 'sport' && sportId && (
        <Card className="content-card" title={`${sportMeta(sportId).label} · 下属赛事`} style={{ marginTop: 16 }}>
          <Button type="link" onClick={() => goLevel('match')}>在工作台查看全部并筛选 →</Button>
          <Table
            size="small"
            rowKey="matchCode"
            dataSource={rows.filter((r) => ofSport(r) === sportId)}
            pagination={{ pageSize: 30 }}
            columns={columns.filter((c) => c.title !== '球类')}
            expandable={matchListExpandable}
            scroll={{ x: 1100 }}
          />
        </Card>
      )}

      {level === 'league' && sportId && (
        <Card
          className="content-card"
          title={`${leagueTitle({
            leagueCode: leagueId || 'UNKNOWN',
            leagueName: leagueStats.find((l) => l.id === (leagueId || 'UNKNOWN'))?.name,
          })} · 下属赛事`}
          style={{ marginTop: 16 }}
          extra={(
            <Button type="link" onClick={() => goLevel('match')}>
              在工作台查看并筛选 →
            </Button>
          )}
        >
          <Table
            size="small"
            rowKey="matchCode"
            dataSource={rows.filter(
              (r) => ofSport(r) === sportId && ofLeague(r) === (leagueId || 'UNKNOWN'),
            )}
            pagination={{ pageSize: 30 }}
            columns={columns}
            expandable={matchListExpandable}
            scroll={{ x: 1100 }}
            locale={{ emptyText: '该联赛暂无赛事；Flink 空壳同步或补全元数据后会出现在此' }}
          />
        </Card>
      )}

      <MatchDutyDrawer
        open={!!drawerCode}
        matchCode={drawerCode}
        onClose={closeDrawer}
        onSaved={() => void refresh(true)}
      />
    </div>
  );
}

function MatchInlineMonitor({
  matchCode,
  cache,
  onRetry,
  onOpenConfig,
  onBetTrial,
}: {
  matchCode: string;
  cache?: MatchExpandCache[string];
  onRetry: () => void;
  onOpenConfig: () => void;
  onBetTrial: () => void;
}) {
  if (!cache || (cache.loading && !cache.view)) {
    return (
      <div className="liability-inline-loading" style={{ padding: '12px 0' }}>
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
      <div className="liability-inline-loading" style={{ padding: '12px 0' }}>
        <Spin size="small" tip={`加载 ${matchCode}…`} />
      </div>
    );
  }
  return (
    <div className="liability-inline-panel" onClick={(e) => e.stopPropagation()} style={{ padding: '4px 8px 12px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8, gap: 12 }}>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {matchupLabel(view.homeTeam, view.awayTeam, matchCode)}
          {cache.loading ? ' · 刷新中…' : ''}
        </Typography.Text>
        <Space wrap size={8}>
          <Button size="small" type="primary" disabled={view.status === 'SUSPENDED'} onClick={onBetTrial}>
            投注试算
          </Button>
          <Button size="small" onClick={onOpenConfig}>
            配置
          </Button>
        </Space>
      </div>
      <MatchInterceptSummary liveView={cache.fixture} match={view} />
      <div style={{ marginTop: 8 }}>
        <MatchMarketsCollapse view={view} />
      </div>
    </div>
  );
}

function StatisticLike({
  icon,
  label,
  value,
  danger,
  hint,
}: {
  icon?: ReactNode;
  label: string;
  value: ReactNode;
  danger?: boolean;
  hint?: string;
}) {
  return (
    <div>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        {icon} {label}
      </Typography.Text>
      <div style={{ fontSize: 22, fontWeight: 600, color: danger ? '#cf1322' : undefined }}>
        {value}
      </div>
      {hint && (
        <Typography.Text type="secondary" style={{ fontSize: 11 }}>
          {hint}
        </Typography.Text>
      )}
    </div>
  );
}
