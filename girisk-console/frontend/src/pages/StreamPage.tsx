import { Button, Card, Col, Empty, Row, Space, Statistic, Table, Tag, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { riskApi } from '../api/riskClient';
import SandboxTabs from '../components/SandboxTabs';
import { useDisplayTimeZone } from '../hooks/useDisplayTimeZone';
import type { RiskDecisionLog, RiskEvaluateResponse, RiskEvent, StreamStatus } from '../types';
import { formatInTimeZone } from '../utils/datetime';
import { DecisionTag, LevelTag } from '../utils/tags';

type LiveRow = RiskEvaluateResponse & { receivedAt: string };

const severityColor: Record<string, string> = { INFO: 'blue', WARN: 'orange', ERROR: 'red' };

export default function StreamPage() {
  const [status, setStatus] = useState<StreamStatus | null>(null);
  const [liveFeed, setLiveFeed] = useState<LiveRow[]>([]);
  const [recent, setRecent] = useState<RiskDecisionLog[]>([]);
  const [events, setEvents] = useState<RiskEvent[]>([]);
  const [eventsLoading, setEventsLoading] = useState(true);
  const [sseOk, setSseOk] = useState(false);
  const displayTz = useDisplayTimeZone();

  const refreshStatus = () => riskApi.streamStatus().then(setStatus).catch(() => setStatus(null));
  const refreshRecent = () => riskApi.decisions(30).then(setRecent).catch(() => setRecent([]));

  useEffect(() => {
    refreshStatus();
    refreshRecent();
    api.events(100).then(setEvents).finally(() => setEventsLoading(false));
    const timer = setInterval(() => {
      refreshStatus();
      refreshRecent();
    }, 5000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    const source = new EventSource('/api/v1/stream/events');
    source.onopen = () => setSseOk(true);
    source.onerror = () => setSseOk(false);
    source.addEventListener('decision', (e) => {
      try {
        const data = JSON.parse(e.data) as RiskEvaluateResponse;
        const row: LiveRow = { ...data, receivedAt: new Date().toLocaleTimeString() };
        setLiveFeed((prev) => [row, ...prev].slice(0, 40));
        setSseOk(true);
      } catch { /* ignore */ }
    });
    return () => source.close();
  }, []);

  const liveStats = useMemo(() => {
    const pass = liveFeed.filter((d) => d.decision === 'PASS').length;
    const reject = liveFeed.filter((d) => d.decision === 'REJECT').length;
    const review = liveFeed.filter((d) => d.decision === 'REVIEW').length;
    return { pass, reject, review, total: liveFeed.length };
  }, [liveFeed]);

  const errorRate = status && status.processedCount > 0
    ? ((status.errorCount / (status.processedCount + status.errorCount)) * 100).toFixed(1)
    : '0.0';

  return (
    <>
      <div className="page-header">
        <h2>调试沙箱</h2>
        <p>管线健康、SSE 推送与本地事件 — 联调观察用，不是责任盘 / 决策 SOT</p>
      </div>
      <SandboxTabs />

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={8} lg={4}>
          <Card className="content-card" size="small">
            <Statistic title="运行模式" value={status?.mode === 'REALTIME_KAFKA' ? 'Kafka' : '本地'} />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card className="content-card" size="small">
            <Statistic title="已处理" value={status?.processedCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card className="content-card" size="small">
            <Statistic title="错误数" value={status?.errorCount ?? 0} valueStyle={{ color: (status?.errorCount ?? 0) > 0 ? '#cf1322' : undefined }} />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card className="content-card" size="small">
            <Statistic title="错误率" value={errorRate} suffix="%" />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card className="content-card" size="small">
            <Statistic title="SSE 订阅" value={status?.sseSubscribers ?? 0} />
          </Card>
        </Col>
        <Col xs={12} sm={8} lg={4}>
          <Card className="content-card" size="small">
            <Statistic
              title="SSE 连接"
              valueRender={() => (
                <Tag color={sseOk ? 'success' : 'default'} style={{ margin: 0, fontSize: 16, padding: '2px 10px' }}>
                  {sseOk ? '已连接' : '未连接'}
                </Tag>
              )}
            />
          </Card>
        </Col>
      </Row>

      <Card
        className="content-card"
        title="管线详情"
        extra={
          <Space>
            <Link to="/girisk/sandbox/order"><Button type="primary" size="small">去发单 / Burst</Button></Link>
            <Link to="/girisk/decisions"><Button size="small">决策中心（生产）</Button></Link>
          </Space>
        }
        style={{ marginBottom: 16 }}
      >
        <Space wrap size={[24, 8]}>
          <span>Kafka：<Tag color={status?.kafkaEnabled ? 'success' : 'default'}>{status?.kafkaEnabled ? '已启用' : '未启用'}</Tag></span>
          {status?.kafkaEnabled && (
            <>
              <Typography.Text type="secondary">Broker {status.bootstrapServers}</Typography.Text>
              <Typography.Text type="secondary">订单 Topic {status.orderTopic}</Typography.Text>
              <Typography.Text type="secondary">决策 Topic {status.decisionTopic}</Typography.Text>
            </>
          )}
          {!status?.kafkaEnabled && (
            <Typography.Text type="secondary">本地模式：mock 订单直接进处理器并经 SSE 推送</Typography.Text>
          )}
        </Space>
      </Card>

      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col xs={24} lg={14}>
          <Card
            className="content-card"
            title={
              <Space>
                <span>实时决策流</span>
                <Tag>{liveStats.total} 条本页</Tag>
                {liveStats.pass > 0 && <Tag color="success">PASS {liveStats.pass}</Tag>}
                {liveStats.reject > 0 && <Tag color="error">REJECT {liveStats.reject}</Tag>}
                {liveStats.review > 0 && <Tag color="warning">REVIEW {liveStats.review}</Tag>}
              </Space>
            }
          >
            {liveFeed.length === 0 ? (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="暂无实时推送。到订单试算切「流管线注入」发一笔或 Burst"
              >
                <Link to="/girisk/sandbox/order"><Button type="primary">打开订单试算</Button></Link>
              </Empty>
            ) : (
              <Table
                size="small"
                rowKey={(r) => `${r.orderId}-${r.receivedAt}-${r.requestId}`}
                dataSource={liveFeed}
                pagination={{ pageSize: 10, size: 'small' }}
                columns={[
                  { title: '时间', dataIndex: 'receivedAt', width: 90 },
                  { title: '订单', dataIndex: 'orderId', width: 160, ellipsis: true },
                  { title: '决策', dataIndex: 'decision', width: 90, render: (v: string) => <DecisionTag value={v} /> },
                  { title: '等级', dataIndex: 'riskLevel', width: 90, render: (v: string) => <LevelTag value={v} /> },
                  { title: '分数', dataIndex: 'riskScore', width: 70 },
                  { title: '耗时', dataIndex: 'latencyMs', width: 70, render: (v: number) => `${v}ms` },
                  { title: '原因', dataIndex: 'reason', ellipsis: true },
                ]}
              />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card className="content-card" title="近期决策快照" extra={<Link to="/girisk/decisions">决策中心</Link>}>
            <Table
              size="small"
              rowKey="id"
              dataSource={recent}
              pagination={false}
              scroll={{ y: 420 }}
              columns={[
                { title: '时间', dataIndex: 'createdAt', width: 150, ellipsis: true, render: (v: string) => formatInTimeZone(v, displayTz) },
                { title: '场景', dataIndex: 'scenario', width: 100, ellipsis: true },
                {
                  title: '决策',
                  dataIndex: 'decision',
                  width: 80,
                  render: (v: string) => <DecisionTag value={v} />,
                },
                { title: '原因', dataIndex: 'reason', ellipsis: true },
              ]}
            />
          </Card>
        </Col>
      </Row>

      <Card className="content-card" title="本地事件日志" extra={<Typography.Text type="secondary">Console 事件表，非 Flink 责任链</Typography.Text>}>
        <Table
          size="small"
          rowKey="id"
          loading={eventsLoading}
          dataSource={events}
          scroll={{ x: 1000 }}
          pagination={{ pageSize: 10, size: 'small' }}
          columns={[
            { title: '时间', dataIndex: 'createdAt', width: 180, render: (v: string) => formatInTimeZone(v, displayTz) },
            { title: '类型', dataIndex: 'eventType', width: 120, render: (v: string) => <Tag>{v}</Tag> },
            { title: '级别', dataIndex: 'severity', width: 80, render: (v: string) => <Tag color={severityColor[v]}>{v}</Tag> },
            { title: '标题', dataIndex: 'title', width: 180 },
            { title: '订单', dataIndex: 'orderId', width: 160 },
            { title: '详情', dataIndex: 'detail', ellipsis: true },
          ]}
        />
      </Card>
    </>
  );
}
