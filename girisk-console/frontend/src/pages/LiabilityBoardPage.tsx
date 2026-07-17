import {
  AlertOutlined,
  AppstoreOutlined,
  ArrowLeftOutlined,
  DollarOutlined,
  RightOutlined,
  TrophyOutlined,
} from '@ant-design/icons';
import {
  Breadcrumb,
  Button,
  Card,
  Col,
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
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
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
import { marketGroupKey, stakesFromGroups } from '../utils/marketGroupKey';
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

/** 总体 / 球类 / 联赛 / 赛事：每层可直进列表，再点进详情 */
type Level = ExposureLevel;

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
  const [editStakes, setEditStakes] = useState<Record<string, Record<string, number>>>({});
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [statusSaving, setStatusSaving] = useState(false);
  const [demoLoading, setDemoLoading] = useState(false);
  const [hydrated, setHydrated] = useState(false);

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

  const openMatch = useCallback(async (code: string) => {
    setDetailLoading(true);
    setDetailError(null);
    setMatchFixture(null);
    try {
      const view = await sportsApi.match(code);
      setDetail(view);
      setEditStakes(stakesFromGroups(view.marketGroups));
      try {
        setMatchFixture(await riskApi.fixture(code));
      } catch {
        setMatchFixture(null);
      }
    } catch (e) {
      setDetail(null);
      setDetailError((e as Error).message || '加载赛事失败');
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [d, fx] = await Promise.all([
        sportsApi.dashboard(),
        riskApi.fixturesTop(8).catch(() => [] as RiskFixtureView[]),
      ]);
      setDash(d);
      setFixtures(fx);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  // 无 URL 参数时恢复上次选择
  useEffect(() => {
    if (hydrated) return;
    const hasUrl = !!(levelParam || sportId || leagueId || matchCode || filterOver);
    if (!hasUrl) {
      const saved = readExposureNav();
      if (saved?.level) {
        setQuery({
          level: saved.level,
          sport: saved.sport,
          league: saved.league,
          match: saved.match,
          filter: saved.filter,
        });
      } else {
        setQuery({ level: 'overall' });
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

  const openFixture = (r: RiskFixtureView) => {
    const m = resolveMatchFromFixture(r, allMatches);
    if (!m) {
      message.warning(`场次 ${r.fixtureId} 尚未接入敞口赛事库，无法下钻盘口`);
      return;
    }
    enterMatch(m);
  };

  const updateStake = (group: MarketGroupView, selection: string, value: number | null) => {
    const key = marketGroupKey(group);
    setEditStakes((prev) => ({
      ...prev,
      [key]: { ...prev[key], [selection]: value ?? 0 },
    }));
  };

  const rowsForGroup = (g: MarketGroupView) => {
    const stakes = editStakes[marketGroupKey(g)] ?? g.stakes;
    return buildOutcomeRows(Object.keys(g.stakes), stakes, detail?.delta ?? 0.2);
  };

  const simulatedTotal = useMemo(() => {
    if (!detail) return 0;
    return Math.round(detail.marketGroups.reduce((sum, g) => {
      const stakes = editStakes[marketGroupKey(g)] ?? g.stakes;
      return sum + groupStakeTotal(stakes);
    }, 0) * 100) / 100;
  }, [detail, editStakes]);

  const runCheck = async (code: string) => {
    try {
      const view = await sportsApi.exposureCheck(code);
      setDetail(view);
      setEditStakes(stakesFromGroups(view.marketGroups));
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

  /** 顶栏切换：直接进入该层；带上上次选中的实体（无则显示列表） */
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
      match: matchCode || saved?.match,
      filter: f,
    });
  };

  if (loading && !dash) {
    return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />;
  }

  return (
    <div className="liability-board">
      <div className="page-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16 }}>
        <div>
          <h2 style={{ marginBottom: 4 }}>敞口看板</h2>
          <p style={{ margin: 0 }}>
            每层可直进列表 · 点进详情 · 总体/球类/联赛/赛事均可改限额（赛事 &gt; 联赛 &gt; 球类 &gt; 总体）
          </p>
        </div>
        <Space wrap>
          <Segmented
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
          <Button loading={demoLoading} onClick={() => loadDemoData(true)}>重灌演示</Button>
          <Button onClick={refresh}>刷新</Button>
        </Space>
      </div>

      <div className="liability-rail">
        <Segmented
          value={level}
          onChange={(v) => goLevel(v as Level)}
          options={[
            { label: '总体', value: 'overall' },
            { label: '球类', value: 'sport' },
            { label: '联赛', value: 'league' },
            { label: '赛事', value: 'match' },
          ]}
        />
      </div>

      <Breadcrumb
        style={{ marginBottom: 16 }}
        items={[
          { title: <a onClick={() => setQuery({ level: 'overall', filter: filterOver ? 'over' : undefined })}>总体</a> },
          ...(level !== 'overall' ? [{
            title: <a onClick={() => setQuery({ level: 'sport', filter: filterOver ? 'over' : undefined })}>球类</a>,
          }] : []),
          ...(sportId && (level === 'sport' || level === 'league' || level === 'match') ? [{
            title: (
              <a onClick={() => setQuery({
                level: 'sport',
                sport: sportId,
                filter: filterOver ? 'over' : undefined,
              })}
              >
                {sportMeta(sportId).label}
              </a>
            ),
          }] : []),
          ...(level === 'league' || level === 'match' ? [{
            title: (
              <a onClick={() => setQuery({
                level: 'league',
                sport: sportId,
                filter: filterOver ? 'over' : undefined,
              })}
              >
                联赛
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
                {currentLeagueName}
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
                赛事
              </a>
            ),
          }] : []),
          ...(matchCode && level === 'match' ? [{
            title: detail ? `${detail.homeTeam} vs ${detail.awayTeam}` : matchCode,
          }] : []),
        ]}
      />

      {level === 'overall' && dash && (
        <div className="liability-pane">
          <ScopeLimitDutyBar
            mode="overall"
            title="总体限额（值班）"
            hint="平台默认；球类/联赛/赛事未单独覆盖时继承此处。"
            onSaved={refresh}
          />
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} lg={6}>
              <Card className="stat-card" hoverable onClick={() => setQuery({ level: 'match', filter: filterOver ? 'over' : undefined })}>
                <Typography.Text type="secondary">在管赛事</Typography.Text>
                <div className="liability-kpi"><TrophyOutlined /> {dash.matchCount}</div>
                <div className="liability-kpi-hint">查看全部赛事 <RightOutlined /></div>
              </Card>
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Card className="stat-card">
                <Typography.Text type="secondary">盘口数</Typography.Text>
                <div className="liability-kpi" style={{ color: '#1677ff' }}><AppstoreOutlined /> {dash.outcomeCount}</div>
              </Card>
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Card className="stat-card" hoverable onClick={() => setQuery({ level: 'match', filter: 'over' })}>
                <Typography.Text type="secondary">超额盘口</Typography.Text>
                <div className="liability-kpi" style={{ color: dash.overLimitOutcomeCount ? '#ff4d4f' : '#8c8c8c' }}>
                  <AlertOutlined /> {dash.overLimitOutcomeCount}
                </div>
              </Card>
            </Col>
            <Col xs={24} sm={12} lg={6}>
              <Card className="stat-card">
                <Typography.Text type="secondary">总投注（本金）</Typography.Text>
                <div className="liability-kpi"><DollarOutlined /> {Number(dash.totalStake).toLocaleString()}</div>
              </Card>
            </Col>
          </Row>

          <Typography.Title level={5} style={{ marginTop: 24, marginBottom: 12 }}>球类速览</Typography.Title>
          <Row gutter={[16, 16]}>
            {sportStats.map((s) => (
              <Col xs={24} md={12} key={s.id}>
                <Card
                  className="content-card liability-sport-card"
                  hoverable
                  onClick={() => setQuery({ level: 'sport', sport: s.id, filter: filterOver ? 'over' : undefined })}
                >
                  <Space align="start" style={{ width: '100%', justifyContent: 'space-between' }}>
                    <div>
                      <Typography.Title level={4} style={{ margin: 0 }}>{s.emoji} {s.label}</Typography.Title>
                      <Typography.Text type="secondary">{s.leagueCount} 个联赛 · {s.matchCount} 场比赛</Typography.Text>
                    </div>
                    <Button
                      type="primary"
                      onClick={(e) => {
                        e.stopPropagation();
                        setQuery({ level: 'sport', sport: s.id, filter: filterOver ? 'over' : undefined });
                      }}
                    >
                      进入球类 <RightOutlined />
                    </Button>
                  </Space>
                  <Row gutter={16} style={{ marginTop: 16 }}>
                    <Col span={12}><Typography.Text type="secondary">投注合计</Typography.Text><div>{s.totalStake.toLocaleString()}</div></Col>
                    <Col span={12}><Typography.Text type="secondary">限额中</Typography.Text><div>{s.limitModeCount}</div></Col>
                  </Row>
                </Card>
              </Col>
            ))}
          </Row>

          <Card
            className="content-card"
            title="高危赛事（物化视图）"
            extra={(
              <Button type="link" onClick={() => setQuery({ level: 'match', filter: filterOver ? 'over' : undefined })}>
                全部赛事
              </Button>
            )}
            style={{ marginTop: 16 }}
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
                { title: '接单', width: 70, render: (_: unknown, r: RiskFixtureView) => r.replayStats?.acceptedCount ?? r.confirmedOrders },
                {
                  title: '拦截',
                  width: 90,
                  render: (_: unknown, r: RiskFixtureView) => {
                    const s = r.replayStats;
                    if (!s) return '—';
                    return `${s.rejectedTotal ?? 0}`;
                  },
                },
                { title: '最差亏损', dataIndex: 'worstLossCents', width: 110, render: (v: number) => `¥${(v / 100).toLocaleString()}` },
                { title: '最差比分', dataIndex: 'worstScore', width: 80, render: (v?: string) => v || '—' },
                { title: '等级', dataIndex: 'riskLevel', width: 90, render: (v: string) => <LevelTag value={v} /> },
                {
                  title: '',
                  width: 88,
                  render: (_: unknown, r: RiskFixtureView) => (
                    <Button type="link" size="small" onClick={() => openFixture(r)}>
                      进赛事 <RightOutlined />
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
            <Card className="content-card" title="全部球类">
              <Row gutter={[16, 16]}>
                {sportStats.map((s) => (
                  <Col xs={24} md={12} key={s.id}>
                    <Card
                      className="liability-match-card"
                      hoverable
                      onClick={() => setQuery({ level: 'sport', sport: s.id, filter: filterOver ? 'over' : undefined })}
                      title={`${s.emoji} ${s.label}`}
                    >
                      <Row gutter={12} style={{ marginBottom: 12 }}>
                        <Col span={8}><Typography.Text type="secondary">联赛</Typography.Text><div style={{ fontWeight: 600 }}>{s.leagueCount}</div></Col>
                        <Col span={8}><Typography.Text type="secondary">赛事</Typography.Text><div style={{ fontWeight: 600 }}>{s.matchCount}</div></Col>
                        <Col span={8}><Typography.Text type="secondary">限额中</Typography.Text><div style={{ fontWeight: 600 }}>{s.limitModeCount}</div></Col>
                      </Row>
                      <Button type="primary" block>进入球类 <RightOutlined /></Button>
                    </Card>
                  </Col>
                ))}
              </Row>
            </Card>
          ) : (
            <>
              <ScopeLimitDutyBar
                mode="sport"
                sportCode={sportId}
                title={`${sportMeta(sportId).label} · 球类限额`}
                onSaved={refresh}
              />
              <Card
                className="content-card"
                title={`${sportMeta(sportId).label} · 联赛列表`}
                extra={(
                  <Button type="link" icon={<ArrowLeftOutlined />} onClick={() => setQuery({ level: 'sport', filter: filterOver ? 'over' : undefined })}>
                    全部球类
                  </Button>
                )}
              >
                {leagueStats.length === 0 ? (
                  <Empty description="该球类暂无联赛" />
                ) : (
                  <Row gutter={[16, 16]}>
                    {leagueStats.map((l) => (
                      <Col xs={24} md={12} key={`${l.sport}:${l.id}`}>
                        <Card
                          className="liability-match-card"
                          hoverable
                          onClick={() => setQuery({
                            level: 'league',
                            sport: l.sport,
                            league: l.id,
                            filter: filterOver ? 'over' : undefined,
                          })}
                          title={l.name}
                          extra={<Typography.Text type="secondary">{l.id}</Typography.Text>}
                        >
                          <Row gutter={12} style={{ marginBottom: 16 }}>
                            <Col span={8}>
                              <Typography.Text type="secondary">赛事数</Typography.Text>
                              <div style={{ fontWeight: 600 }}>{l.matchCount}</div>
                            </Col>
                            <Col span={8}>
                              <Typography.Text type="secondary">投注合计</Typography.Text>
                              <div style={{ fontWeight: 600 }}>{l.totalStake.toLocaleString()}</div>
                            </Col>
                            <Col span={8}>
                              <Typography.Text type="secondary">限额中</Typography.Text>
                              <div style={{ fontWeight: 600 }}>{l.limitModeCount}</div>
                            </Col>
                          </Row>
                          <Button type="primary" block>进入联赛 <RightOutlined /></Button>
                        </Card>
                      </Col>
                    ))}
                  </Row>
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
              <ScopeLimitDutyBar
                mode="league"
                sportCode={sportId || 'football'}
                leagueCode={leagueId}
                title={`${currentLeagueName} · 联赛限额`}
                onSaved={refresh}
              />
              <Card
                className="content-card"
                title={`${currentLeagueName} · 赛事列表`}
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
                    size="small"
                    rowKey="matchCode"
                    dataSource={leagueMatches}
                    pagination={false}
                    onRow={(m) => ({ onClick: () => enterMatch(m), style: { cursor: 'pointer' } })}
                    columns={[
                      {
                        title: '对阵',
                        render: (_: unknown, m: SportsMatch) => (
                          <Space>
                            <span>{m.homeTeam} vs {m.awayTeam}</span>
                            <Tag color={m.status === 'SUSPENDED' ? 'red' : m.limitMode ? 'orange' : 'green'}>
                              {m.status === 'SUSPENDED' ? '已停盘' : m.limitMode ? '限额中' : '正常'}
                            </Tag>
                          </Space>
                        ),
                      },
                      { title: '代码', dataIndex: 'matchCode', width: 160 },
                      { title: '投注合计', width: 120, render: (_: unknown, m: SportsMatch) => Number(m.currentExposure).toLocaleString() },
                      { title: 'δ', width: 70, render: (_: unknown, m: SportsMatch) => Number(m.delta) },
                      {
                        title: '',
                        width: 90,
                        render: (_: unknown, m: SportsMatch) => (
                          <Button type="link" size="small" onClick={(e) => { e.stopPropagation(); enterMatch(m); }}>
                            详情 <RightOutlined />
                          </Button>
                        ),
                      },
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
            <Card className="content-card" title={filterOver ? '超额赛事' : '全部赛事'}>
              {matchList.length === 0 ? (
                <Empty description="暂无赛事">
                  <Button type="primary" loading={demoLoading} onClick={() => loadDemoData(false)}>灌入演示数据</Button>
                </Empty>
              ) : (
                <Table
                  size="small"
                  rowKey="matchCode"
                  dataSource={matchList}
                  pagination={{ pageSize: 20 }}
                  onRow={(m) => ({ onClick: () => enterMatch(m), style: { cursor: 'pointer' } })}
                  columns={[
                    { title: '球类', width: 80, render: (_: unknown, m: SportsMatch) => sportMeta(ofSport(m)).label },
                    { title: '联赛', width: 140, render: (_: unknown, m: SportsMatch) => leagueTitle(m) },
                    {
                      title: '对阵',
                      render: (_: unknown, m: SportsMatch) => (
                        <Space>
                          <span>{m.homeTeam} vs {m.awayTeam}</span>
                          <Tag color={m.status === 'SUSPENDED' ? 'red' : m.limitMode ? 'orange' : 'green'}>
                            {m.status === 'SUSPENDED' ? '已停盘' : m.limitMode ? '限额中' : '正常'}
                          </Tag>
                        </Space>
                      ),
                    },
                    { title: '投注合计', width: 120, render: (_: unknown, m: SportsMatch) => Number(m.currentExposure).toLocaleString() },
                    { title: '阈值', width: 100, render: (_: unknown, m: SportsMatch) => Number(m.exposureThreshold).toLocaleString() },
                    {
                      title: '',
                      width: 90,
                      render: (_: unknown, m: SportsMatch) => (
                        <Button type="link" size="small" onClick={(e) => { e.stopPropagation(); enterMatch(m); }}>
                          详情 <RightOutlined />
                        </Button>
                      ),
                    },
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
              className="content-card"
              title={
                <Space wrap>
                  <span>{detail.homeTeam} vs {detail.awayTeam}</span>
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
                    赛事列表
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
                  <Button size="small" onClick={() => setEditStakes(stakesFromGroups(detail.marketGroups))}>恢复数据</Button>
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
              <ScopeLimitDutyBar
                mode="match"
                matchCode={detail.matchCode}
                title="赛事限额（值班）"
                hint="本场最高优先级；未覆盖字段继承联赛→球类→总体。"
                onSaved={() => { openMatch(detail.matchCode); refresh(); }}
              />

              <Typography.Title level={5} style={{ marginTop: 0 }}>拦截结果汇总（本场）</Typography.Title>
              <MatchInterceptSummary
                stats={matchFixture?.replayStats}
                fallback={{
                  acceptedStakeYuan: simulatedTotal,
                  maxWorstLossYuan: Number(detail.maxWorstLossYuan ?? detail.exposureThreshold),
                  delta: Number(detail.delta),
                  seedPayoutYuan: detail.seedPayoutYuan ?? matchFixture?.replayStats?.seedPayoutYuan,
                }}
              />

              <div style={{ margin: '28px 0 12px' }}>
                <Typography.Title level={5} style={{ margin: 0 }}>
                  各盘口明细
                  <Typography.Text type="secondary" style={{ fontSize: 13, fontWeight: 400, marginLeft: 12 }}>
                    {detail.marketGroups.length} 个盘口 · 本场投注合计 {simulatedTotal.toLocaleString()}
                  </Typography.Text>
                </Typography.Title>
              </div>

              {detail.marketGroups.length === 0 ? (
                <Empty description="该赛事暂无盘口数据" />
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
                          editable: true,
                          onStakeChange: (selection, value) => updateStake(g, selection, value),
                        })}
                      />
                    </Card>
                  );
                })
              )}
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

      <style>{`
        .liability-rail { margin-bottom: 16px; }
        .liability-pane { animation: liabilityIn .28s ease; }
        @keyframes liabilityIn {
          from { opacity: 0; transform: translateY(6px); }
          to { opacity: 1; transform: none; }
        }
        .liability-kpi {
          font-size: 28px; font-weight: 600; margin-top: 8px;
          display: flex; align-items: center; gap: 8px;
        }
        .liability-kpi-hint {
          margin-top: 8px; font-size: 12px; color: #8c8c8c;
          display: flex; align-items: center; gap: 4px;
        }
        .liability-sport-card:hover { transform: translateY(-2px); transition: transform .2s; }
        .liability-sport-pill.active { border-color: #1677ff; background: #f0f5ff; }
        .liability-match-card:hover { border-color: #1677ff; }
        .liability-market.active { border-color: #1677ff; box-shadow: 0 0 0 1px #1677ff22; }
        .row-over-limit td { background: #fff2f0 !important; }
      `}</style>
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

/** 赛事层：对齐产品 HTML「拦截结果汇总」 */
function MatchInterceptSummary({
  stats,
  fallback,
}: {
  stats?: FixtureReplayStats | null;
  fallback?: { acceptedStakeYuan?: number; maxWorstLossYuan?: number; delta?: number; seedPayoutYuan?: number };
}) {
  if (!stats) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="暂无整场拦截回放统计（请先跑 scripts/demo-germany-exposure.sh）"
        style={{ margin: '8px 0 16px' }}
      />
    );
  }
  const rejected = stats.rejectedTotal ?? (stats.rejectedLimit ?? 0) + (stats.rejectedExposure ?? 0);
  const seed = stats.seedPayoutYuan ?? fallback?.seedPayoutYuan ?? 5000;
  const threshold = stats.maxWorstLossYuan ?? fallback?.maxWorstLossYuan ?? 200000;
  const delta = stats.delta ?? fallback?.delta ?? 0.2;

  return (
    <div>
      <Row gutter={[12, 12]}>
        <StatTile label="有效订单数" value={fmtNum(stats.totalOrders, 0)} />
        <StatTile label="接收订单数" value={fmtNum(stats.acceptedCount, 0)} tone="ok" />
        <StatTile label="拦截订单数" value={fmtNum(rejected, 0)} tone="danger" />
        <StatTile label="限额拦截数" value={fmtNum(stats.rejectedLimit, 0)} tone="danger" />
        <StatTile label="风险拦截数" value={fmtNum(stats.rejectedExposure, 0)} tone="danger" />
        <StatTile label="初始已投注金额/盘口" value={fmtNum(seed)} />
        <StatTile label="风险阈值 / 盈亏线" value={`${fmtNum(threshold)} / -${fmtNum(threshold)}`} />
        <StatTile label="完全不拦截最差盈亏" value={fmtNum(stats.noRiskWorstPnlYuan)} tone="danger" />
        <StatTile label="完全不拦截最差比分" value={stats.noRiskWorstScore || '—'} />
        <StatTile label="拦截后最差盈亏" value={fmtNum(stats.withRiskWorstPnlYuan)} tone="danger" />
        <StatTile label="拦截后最差比分" value={stats.withRiskWorstScore || '—'} />
        <StatTile label="拦截后累计投注金额" value={fmtNum(stats.acceptedStakeYuan)} />
      </Row>
      <Typography.Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0, fontSize: 13 }}>
        共处理有效订单 {fmtNum(stats.totalOrders, 0)} 条，接收 {fmtNum(stats.acceptedCount, 0)} 条，
        拦截 {fmtNum(rejected, 0)} 条。其中限额拦截 {fmtNum(stats.rejectedLimit, 0)} 条、
        风险敞口拦截 {fmtNum(stats.rejectedExposure, 0)} 条。
        参数：种子 {fmtNum(seed)} · δ={delta} · 风险阈值 {fmtNum(threshold)}。
      </Typography.Paragraph>
    </div>
  );
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
