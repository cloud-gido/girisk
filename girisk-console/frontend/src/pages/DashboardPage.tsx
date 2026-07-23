import {
  CheckCircleOutlined, CloseCircleOutlined, ClockCircleOutlined, FundProjectionScreenOutlined, ThunderboltOutlined,
} from '@ant-design/icons';
import { Card, Col, Row, Spin, Statistic, Table, Tag, message } from 'antd';
import { Pie } from '@ant-design/charts';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import { sportsApi } from '../api/sportsClient';
import StatLinkCard from '../components/StatLinkCard';
import type { DashboardOverview, RiskFixtureView, SportsDashboardSummary } from '../types';
import { buildExposureMatchUrl, resolveMatchFromFixture } from '../utils/exposureNav';
import { LevelTag } from '../utils/tags';

export default function DashboardPage() {
  const [data, setData] = useState<DashboardOverview | null>(null);
  const [exposure, setExposure] = useState<SportsDashboardSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    Promise.all([
      api.dashboard(),
      sportsApi.dashboard().catch(() => null),
    ]).then(([d, e]) => {
      setData(d);
      setExposure(e);
    }).finally(() => setLoading(false));
  }, []);

  if (loading) return <Spin size="large" style={{ display: 'block', marginTop: 100 }} />;

  const pieData = (data?.decisionTrend || []).map((d) => ({
    type: d.decision === 'PASS' ? '通过'
      : d.decision === 'REJECT' ? '拒绝'
        : d.decision === 'LIMIT' ? '限额'
          : '审核',
    value: d.cnt,
  }));

  const resolveFixtureNav = (r: RiskFixtureView) => {
    const m = resolveMatchFromFixture(r, exposure?.matches ?? []);
    if (!m) {
      message.warning(`场次 ${r.fixtureId} 尚未接入敞口赛事库`);
      navigate('/girisk/exposure');
      return;
    }
    navigate(buildExposureMatchUrl(m));
  };

  const fixtureColumns = [
    {
      title: '场次',
      dataIndex: 'fixtureId',
      render: (_: string, r: RiskFixtureView) => (
        <a onClick={() => resolveFixtureNav(r)}>
          {r.homeTeam} vs {r.awayTeam}
        </a>
      ),
    },
    { title: '确认单', dataIndex: 'confirmedOrders', width: 80 },
    { title: '预留', dataIndex: 'pendingReserved', width: 70 },
    {
      title: '最差亏损',
      dataIndex: 'worstLossCents',
      width: 110,
      render: (v: number) => `¥${(v / 100).toLocaleString()}`,
    },
    { title: '最差比分', dataIndex: 'worstScore', width: 90 },
    { title: '滚球', dataIndex: 'liveScore', width: 80, render: (v: string) => v || '-' },
    { title: '等级', dataIndex: 'riskLevel', width: 90, render: (v: string) => <LevelTag value={v} /> },
  ];

  return (
    <>
      <div className="page-header">
        <h2>总览</h2>
        <p>统一决策态势 · 赛事敞口 · 配置版本 · 审核积压</p>
      </div>
      <Row gutter={[16, 16]} className="dashboard-stat-row">
        <Col xs={24} sm={12} lg={6}>
          <StatLinkCard
            title="总决策数"
            value={data?.totalDecisions ?? 0}
            prefix={<ThunderboltOutlined />}
            valueStyle={{ color: '#1677ff' }}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatLinkCard
            title="通过率"
            value={data?.totalDecisions ? Math.round((data.passCount / data.totalDecisions) * 100) : 0}
            suffix="%"
            prefix={<CheckCircleOutlined />}
            valueStyle={{ color: '#52c41a' }}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatLinkCard
            title="拒绝 / 限额"
            value={`${data?.rejectCount ?? 0} / ${data?.limitCount ?? 0}`}
            prefix={<CloseCircleOutlined />}
            valueStyle={{ color: '#ff4d4f' }}
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatLinkCard
            title="待审工单"
            value={data?.pendingCases ?? 0}
            prefix={<ClockCircleOutlined />}
            valueStyle={{ color: '#faad14' }}
            to="/girisk/cases"
            hint="进入审核工单"
          />
        </Col>
      </Row>

      <Row gutter={[16, 16]} className="dashboard-stat-row" style={{ marginTop: 16 }}>
        <Col xs={24} sm={12} lg={6}>
          <StatLinkCard
            title="超额盘口"
            value={exposure?.overLimitOutcomeCount ?? data?.highRiskFixtures ?? 0}
            prefix={<FundProjectionScreenOutlined />}
            valueStyle={{ color: (exposure?.overLimitOutcomeCount ?? 0) > 0 ? '#ff4d4f' : '#8c8c8c' }}
            to="/girisk/exposure?filter=over"
            hint="打开敞口看板"
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatLinkCard
            title="在管赛事"
            value={exposure?.matchCount ?? 0}
            to="/girisk/exposure"
            hint="查看敞口总览"
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatLinkCard
            title="生产配置 Epoch"
            value={data?.publishedConfigEpoch ?? 0}
            to="/girisk/config"
            hint="打开配置中心"
          />
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <StatLinkCard
            title="平均延迟"
            value={data?.avgLatencyMs ?? 0}
            suffix="ms"
          />
        </Col>
      </Row>

      <Row gutter={[16, 16]} className="dashboard-panel-row" style={{ marginTop: 16 }}>
        <Col xs={24} lg={8}>
          <Card className="content-card" title="决策分布">
            <Pie data={pieData} angleField="value" colorField="type" radius={0.8} innerRadius={0.5} height={260} legend={{ position: 'bottom' }} />
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card className="content-card" title="系统指标">
            <Row gutter={[16, 24]}>
              <Col span={12}><Statistic title="活跃规则" value={data?.activeRules} /></Col>
              <Col span={12}><Statistic title="名单条目" value={data?.listEntries} /></Col>
              <Col span={12}><Statistic title="审核中" value={data?.reviewCount} /></Col>
              <Col span={12}>
                <Tag color="blue" style={{ cursor: 'pointer' }} onClick={() => navigate('/girisk/config')}>配置中心</Tag>
                <Tag color="cyan" style={{ cursor: 'pointer', marginLeft: 8 }} onClick={() => navigate('/girisk/decisions')}>决策中心</Tag>
                <Tag color="purple" style={{ cursor: 'pointer', marginLeft: 8 }} onClick={() => navigate('/girisk/replay')}>风险回放</Tag>
              </Col>
            </Row>
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card className="content-card" title="拒单 / 限额主因 Top">
            <Table
              size="small"
              pagination={false}
              rowKey={(_, i) => String(i)}
              dataSource={data?.recentRejectReasons || []}
              columns={[
                { title: '决策', dataIndex: 'decision', width: 80, render: (v: string) => <Tag>{v}</Tag> },
                { title: '原因', dataIndex: 'reason', ellipsis: true },
                { title: '次数', dataIndex: 'cnt', width: 60 },
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Card
        className="content-card"
        title="高危赛事敞口"
        extra={<a onClick={() => navigate('/girisk/exposure')}>进入敞口看板 →</a>}
        style={{ marginTop: 16 }}
      >
        <Table
          size="small"
          rowKey="fixtureId"
          pagination={false}
          columns={fixtureColumns}
          dataSource={data?.topFixtures || []}
        />
      </Card>
    </>
  );
}
