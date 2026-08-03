import { Button, Col, Drawer, Form, Input, Row, Select, Space, Spin, Typography, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import ScopeGateDutyBar from './ScopeGateDutyBar';
import ScopeLimitDutyBar from './ScopeLimitDutyBar';
import { sportsApi } from '../api/sportsClient';
import type { SportsMatchListRow, SportsMatchView } from '../types';
import { matchupLabel } from '../utils/matchDisplay';

type Props = {
  matchCode: string | null;
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
};

export default function MatchDutyDrawer({ matchCode, open, onClose, onSaved }: Props) {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [view, setView] = useState<SportsMatchView | null>(null);
  const [statusSaving, setStatusSaving] = useState(false);
  const [metaSaving, setMetaSaving] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async (code: string) => {
    setLoading(true);
    try {
      const v = await sportsApi.match(code);
      setView(v);
      form.setFieldsValue({
        homeTeam: v.homeTeam || '',
        awayTeam: v.awayTeam || '',
        sportCode: v.sportCode || 'football',
        leagueCode: v.leagueCode || '',
        leagueName: v.leagueName || '',
      });
    } catch (e) {
      message.error((e as Error).message || '加载失败');
      setView(null);
    } finally {
      setLoading(false);
    }
  }, [form]);

  useEffect(() => {
    if (open && matchCode) void load(matchCode);
  }, [open, matchCode, load]);

  const saveMeta = async () => {
    if (!matchCode) return;
    const values = await form.validateFields();
    setMetaSaving(true);
    try {
      const v = await sportsApi.updateMatchMeta(matchCode, {
        homeTeam: values.homeTeam || null,
        awayTeam: values.awayTeam || null,
        sportCode: values.sportCode || 'football',
        leagueCode: values.leagueCode || null,
        leagueName: values.leagueName || null,
      });
      setView(v);
      message.success('赛事信息已保存');
      onSaved();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setMetaSaving(false);
    }
  };

  const toggleStatus = async () => {
    if (!view) return;
    const next = view.status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED';
    setStatusSaving(true);
    try {
      const v = await sportsApi.setMatchStatus(view.matchCode, next);
      setView(v);
      message.success(next === 'SUSPENDED' ? '已停盘' : '已开盘');
      onSaved();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setStatusSaving(false);
    }
  };

  const title = view
    ? matchupLabel(view.homeTeam, view.awayTeam, view.matchCode)
    : matchCode || '赛事配置';

  return (
    <Drawer
      width={720}
      open={open}
      onClose={onClose}
      title={title}
      destroyOnClose
      extra={
        view ? (
          <Space>
            <Button
              size="small"
              danger={view.status !== 'SUSPENDED'}
              loading={statusSaving}
              onClick={() => void toggleStatus()}
            >
              {view.status === 'SUSPENDED' ? '开盘' : '停盘'}
            </Button>
            <Button
              size="small"
              type="primary"
              disabled={view.status === 'SUSPENDED'}
              onClick={() => navigate(`/girisk/sandbox/bet?match=${view.matchCode}`)}
            >
              投注试算
            </Button>
          </Space>
        ) : null
      }
    >
      {loading && !view ? (
        <Spin style={{ display: 'block', margin: '40px auto' }} />
      ) : !view ? (
        <Typography.Text type="secondary">无法加载赛事</Typography.Text>
      ) : (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Typography.Text type="secondary" copyable={{ text: view.matchCode }}>
            赛事 ID · {view.matchCode}
          </Typography.Text>

          <div>
            <Typography.Title level={5} style={{ marginTop: 0 }}>赛事信息</Typography.Title>
            <Typography.Paragraph type="secondary" style={{ marginBottom: 8, fontSize: 12 }}>
              缺省可留白；Flink 仅保证赛事 ID，队名/联赛由运营补全。
            </Typography.Paragraph>
            <Form form={form} layout="vertical" size="small">
              <Form.Item name="sportCode" label="球类">
                <Select
                  options={[
                    { value: 'football', label: '足球' },
                    { value: 'basketball', label: '篮球' },
                  ]}
                />
              </Form.Item>
              <Row gutter={12}>
                <Col span={12}>
                  <Form.Item name="homeTeam" label="主队">
                    <Input placeholder="可留白" allowClear />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item name="awayTeam" label="客队">
                    <Input placeholder="可留白" allowClear />
                  </Form.Item>
                </Col>
              </Row>
              <Form.Item name="leagueCode" label="联赛代码">
                <Input placeholder="可留白" allowClear />
              </Form.Item>
              <Form.Item name="leagueName" label="联赛名称">
                <Input placeholder="可留白" allowClear />
              </Form.Item>
              <Button type="primary" loading={metaSaving} onClick={() => void saveMeta()}>
                保存赛事信息
              </Button>
            </Form>
          </div>

          <ScopeGateDutyBar
            mode="match"
            matchCode={view.matchCode}
            onSaved={() => { void load(view.matchCode); onSaved(); }}
          />
          <ScopeLimitDutyBar
            mode="match"
            matchCode={view.matchCode}
            title="赛事限额"
            hint="整场 | 赛前 | 滚球：δ / 种子 / 最差亏损阈值 / 单注返彩上限。赛前/滚球未填继承整场。"
            onSaved={() => { void load(view.matchCode); onSaved(); }}
          />

          <Typography.Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 12 }}>
            拦截汇总与盘口明细请在赛事列表行首箭头展开查看。
          </Typography.Paragraph>
        </Space>
      )}
    </Drawer>
  );
}

export function GateDots({ row }: { row: SportsMatchListRow }) {
  const tip = [
    `开盘 ${row.tradingEnabled ? '开' : '关'}（${row.tradingSource || '-'}）`,
    `限额 ${row.limitGateEnabled ? '开' : '关'}（${row.limitGateSource || '-'}）`,
    `敞口 ${row.exposureGateEnabled ? '开' : '关'}（${row.exposureGateSource || '-'}）`,
  ].join('\n');
  const items: { on: boolean; label: string }[] = [
    { on: row.tradingEnabled, label: '开' },
    { on: row.limitGateEnabled, label: '限' },
    { on: row.exposureGateEnabled, label: '敞' },
  ];
  return (
    <Space size={4} title={tip}>
      {items.map((it) => (
        <Typography.Text
          key={it.label}
          style={{
            fontSize: 11,
            padding: '0 4px',
            borderRadius: 3,
            background: it.on ? 'rgba(82,196,26,.15)' : 'rgba(255,77,79,.15)',
            color: it.on ? '#389e0d' : '#cf1322',
          }}
        >
          {it.label}
        </Typography.Text>
      ))}
    </Space>
  );
}
