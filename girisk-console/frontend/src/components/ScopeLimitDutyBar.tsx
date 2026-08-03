import { Button, Col, Collapse, Form, InputNumber, Row, Segmented, Space, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { sportsApi } from '../api/sportsClient';
import type { FixtureLimitOverrideRequest, ScopeLimitParamsView } from '../types';

type MatchSegment = 'all' | 'pre' | 'live';

type Props = {
  title: string;
  hint?: string;
  /** overall | sport | league | match */
  mode: 'overall' | 'sport' | 'league' | 'match';
  sportCode?: string;
  leagueCode?: string;
  matchCode?: string;
  /** match 模式也可直接用已有 put/clear */
  onSaved?: () => void;
  /** 默认展开 */
  defaultOpen?: boolean;
};

export default function ScopeLimitDutyBar({
  title,
  hint,
  mode,
  sportCode,
  leagueCode,
  matchCode,
  onSaved,
  defaultOpen = false,
}: Props) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const [view, setView] = useState<ScopeLimitParamsView | null>(null);
  const [matchOverrideActive, setMatchOverrideActive] = useState(false);
  const [matchSegment, setMatchSegment] = useState<MatchSegment>('all');

  const load = async (segment: MatchSegment = matchSegment) => {
    try {
      if (mode === 'match') {
        if (!matchCode) return;
        const v = await sportsApi.getLimitOverride(matchCode, segment);
        setMatchOverrideActive(!!v.overrideActive);
        const inheritedDelta = Number(v.baseDelta ?? v.delta);
        const inheritedSeed = Number(v.globalSeedPayoutYuan ?? v.seedPayoutYuan);
        const inheritedMaxWorst = Number(v.globalMaxWorstLossYuan ?? v.maxWorstLossYuan);
        const inheritedMaxBet = v.globalMaxBetPayoutYuan ?? null;
        setView({
          scopeType: segment === 'pre' ? 'MATCH_PRE' : segment === 'live' ? 'MATCH_LIVE' : 'MATCH',
          scopeKey: matchCode,
          delta: Number(v.delta),
          seedPayoutYuan: Number(v.seedPayoutYuan),
          maxWorstLossYuan: Number(v.maxWorstLossYuan),
          maxBetPayoutYuan: v.maxBetPayoutYuan ?? null,
          overrideActive: !!v.overrideActive,
          inheritedDelta,
          inheritedSeedPayoutYuan: inheritedSeed,
          inheritedMaxWorstLossYuan: inheritedMaxWorst,
          inheritedMaxBetPayoutYuan: inheritedMaxBet,
          overrideDelta: v.overrideDelta ?? null,
          overrideSeedPayoutYuan: v.overrideSeedPayoutYuan ?? null,
          overrideMaxWorstLossYuan: v.overrideMaxWorstLossYuan ?? null,
          overrideMaxBetPayoutYuan: v.overrideMaxBetPayoutYuan ?? null,
          updatedBy: v.updatedBy,
          updatedAt: v.updatedAt,
        });
        if (segment === 'all') {
          form.setFieldsValue({
            delta: Number(v.delta),
            seedPayoutYuan: Number(v.seedPayoutYuan),
            maxWorstLossYuan: Number(v.maxWorstLossYuan),
            maxBetPayoutYuan:
              v.maxBetPayoutYuan != null && Number(v.maxBetPayoutYuan) > 0
                ? Number(v.maxBetPayoutYuan)
                : null,
          });
        } else if (v.overrideActive) {
          form.setFieldsValue({
            delta: v.overrideDelta != null ? Number(v.overrideDelta) : null,
            seedPayoutYuan: v.overrideSeedPayoutYuan != null ? Number(v.overrideSeedPayoutYuan) : null,
            maxWorstLossYuan:
              v.overrideMaxWorstLossYuan != null ? Number(v.overrideMaxWorstLossYuan) : null,
            maxBetPayoutYuan:
              v.overrideMaxBetPayoutYuan != null && Number(v.overrideMaxBetPayoutYuan) > 0
                ? Number(v.overrideMaxBetPayoutYuan)
                : null,
          });
        } else {
          form.setFieldsValue({
            delta: null,
            seedPayoutYuan: null,
            maxWorstLossYuan: null,
            maxBetPayoutYuan: null,
          });
        }
        return;
      }
      const v =
        mode === 'league'
          ? await sportsApi.getLeagueScopeOverride(sportCode || 'football', leagueCode || 'UNKNOWN')
          : await sportsApi.getScopeOverride(
              mode === 'overall' ? 'overall' : 'sport',
              mode === 'overall' ? '_' : sportCode || 'football',
            );
      setView(v);
      setMatchOverrideActive(!!v.overrideActive);
      form.setFieldsValue({
        delta: Number(v.delta),
        seedPayoutYuan: Number(v.seedPayoutYuan),
        maxWorstLossYuan: Number(v.maxWorstLossYuan),
        maxBetPayoutYuan:
          v.maxBetPayoutYuan != null && Number(v.maxBetPayoutYuan) > 0
            ? Number(v.maxBetPayoutYuan)
            : null,
      });
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  useEffect(() => {
    void load(matchSegment);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, sportCode, leagueCode, matchCode, matchSegment]);

  const save = async () => {
    const values = await form.validateFields();
    const body: FixtureLimitOverrideRequest = {
      delta: values.delta ?? null,
      seedPayoutYuan: values.seedPayoutYuan ?? null,
      maxWorstLossYuan: values.maxWorstLossYuan ?? null,
      maxBetPayoutYuan: values.maxBetPayoutYuan ?? null,
      operatorId: 'trader',
    };
    setSaving(true);
    try {
      if (mode === 'match') {
        if (!matchCode) return;
        const v = await sportsApi.putLimitOverride(matchCode, body, matchSegment);
        const label =
          matchSegment === 'pre' ? '赛前' : matchSegment === 'live' ? '滚球' : '整场';
        message.success(v.overrideActive ? `${label}覆盖已生效` : `已恢复${label}继承`);
      } else if (mode === 'league') {
        const v = await sportsApi.putLeagueScopeOverride(
          sportCode || 'football',
          leagueCode || 'UNKNOWN',
          body,
        );
        message.success(v.overrideActive ? '联赛覆盖已生效' : '已恢复继承参数');
      } else {
        const v = await sportsApi.putScopeOverride(
          mode === 'overall' ? 'overall' : 'sport',
          mode === 'overall' ? '_' : sportCode || 'football',
          body,
        );
        message.success(v.overrideActive ? '覆盖已生效' : '已恢复继承参数');
      }
      await load(matchSegment);
      onSaved?.();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const clear = async () => {
    setSaving(true);
    try {
      if (mode === 'match') {
        if (!matchCode) return;
        await sportsApi.clearLimitOverride(matchCode, matchSegment);
      } else if (mode === 'league') {
        await sportsApi.clearLeagueScopeOverride(sportCode || 'football', leagueCode || 'UNKNOWN');
      } else {
        await sportsApi.clearScopeOverride(
          mode === 'overall' ? 'overall' : 'sport',
          mode === 'overall' ? '_' : sportCode || 'football',
        );
      }
      message.success('已清除本层覆盖');
      await load(matchSegment);
      onSaved?.();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const segmentHint =
    mode === 'match' && matchSegment !== 'all'
      ? `空字段继承整场生效值（δ=${view?.inheritedDelta ?? '—'} · 阈值=${view?.inheritedMaxWorstLossYuan ?? '—'}）。`
      : '';

  const fieldRequired = mode !== 'match' || matchSegment === 'all';

  return (
    <Collapse
      className="liability-duty-module"
      bordered={false}
      defaultActiveKey={defaultOpen ? ['limits'] : []}
      items={[{
        key: 'limits',
        label: (
          <span className="liability-duty-module-label">
            {title}
            {view ? (
              <Typography.Text type="secondary" style={{ marginLeft: 8, fontWeight: 400 }}>
                {view.overrideActive ? '本层已覆盖' : '继承中'}
                {' · '}
                δ={Number(view.delta)}
              </Typography.Text>
            ) : null}
          </span>
        ),
        extra: (
          <Space onClick={(e) => e.stopPropagation()}>
            <Button size="small" danger disabled={saving || !matchOverrideActive} onClick={clear}>
              清除本层覆盖
            </Button>
            <Button size="small" type="primary" loading={saving} onClick={save}>
              立即生效
            </Button>
          </Space>
        ),
        children: (
          <>
            {mode === 'match' ? (
              <Segmented
                size="small"
                style={{ marginBottom: 12 }}
                value={matchSegment}
                onChange={(v) => setMatchSegment(v as MatchSegment)}
                options={[
                  { label: '整场', value: 'all' },
                  { label: '赛前', value: 'pre' },
                  { label: '滚球', value: 'live' },
                ]}
              />
            ) : null}
            <Typography.Paragraph type="secondary" style={{ marginTop: 0, marginBottom: 12, fontSize: 12 }}>
              {hint
                || (mode === 'match'
                  ? '整场覆盖联赛/球类/总体；赛前/滚球未配字段继承整场。'
                  : '仅改本层；下级未单独覆盖时继承本层。优先级：单赛事 > 联赛 > 球类 > 默认。')}
              {segmentHint}
              {view?.overrideActive ? ' · 本层已覆盖' : ' · 本层未覆盖（显示继承值）'}
            </Typography.Paragraph>
            <Form form={form} layout="vertical">
              <Row gutter={16}>
                <Col xs={24} sm={12} md={6}>
                  <Form.Item
                    name="delta"
                    label="等比例 δ"
                    rules={fieldRequired ? [{ required: true }] : undefined}
                    extra={
                      !fieldRequired && view?.inheritedDelta != null
                        ? `继承 ${view.inheritedDelta}`
                        : undefined
                    }
                    style={{ marginBottom: 8 }}
                  >
                    <InputNumber
                      min={0}
                      max={1}
                      step={0.05}
                      style={{ width: '100%' }}
                      placeholder={
                        !fieldRequired && view?.inheritedDelta != null
                          ? String(view.inheritedDelta)
                          : undefined
                      }
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <Form.Item
                    name="seedPayoutYuan"
                    label="冷启动种子（返彩元）"
                    rules={fieldRequired ? [{ required: true }] : undefined}
                    extra={
                      !fieldRequired && view?.inheritedSeedPayoutYuan != null
                        ? `继承 ${view.inheritedSeedPayoutYuan}`
                        : undefined
                    }
                    style={{ marginBottom: 8 }}
                  >
                    <InputNumber
                      min={0}
                      step={100}
                      style={{ width: '100%' }}
                      placeholder={
                        !fieldRequired && view?.inheritedSeedPayoutYuan != null
                          ? String(view.inheritedSeedPayoutYuan)
                          : undefined
                      }
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <Form.Item
                    name="maxWorstLossYuan"
                    label="最差亏损阈值（元）"
                    rules={fieldRequired ? [{ required: true }] : undefined}
                    extra={
                      !fieldRequired && view?.inheritedMaxWorstLossYuan != null
                        ? `继承 ${view.inheritedMaxWorstLossYuan}`
                        : undefined
                    }
                    style={{ marginBottom: 8 }}
                  >
                    <InputNumber
                      min={0}
                      step={1000}
                      style={{ width: '100%' }}
                      placeholder={
                        !fieldRequired && view?.inheritedMaxWorstLossYuan != null
                          ? String(view.inheritedMaxWorstLossYuan)
                          : undefined
                      }
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <Form.Item
                    name="maxBetPayoutYuan"
                    label="单注返彩上限（元）"
                    extra={
                      !fieldRequired
                        ? `空=继承${view?.inheritedMaxBetPayoutYuan != null ? `（${view.inheritedMaxBetPayoutYuan}）` : ''}；0=不启用`
                        : '空或 0 = 不启用'
                    }
                    style={{ marginBottom: 8 }}
                  >
                    <InputNumber min={0} step={100} style={{ width: '100%' }} placeholder="未启用" />
                  </Form.Item>
                </Col>
              </Row>
            </Form>
          </>
        ),
      }]}
    />
  );
}
