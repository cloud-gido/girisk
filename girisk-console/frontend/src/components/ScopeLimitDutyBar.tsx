import { Button, Card, Col, Form, InputNumber, Row, Space, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { sportsApi } from '../api/sportsClient';
import type { FixtureLimitOverrideRequest, ScopeLimitParamsView } from '../types';

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
};

export default function ScopeLimitDutyBar({
  title,
  hint,
  mode,
  sportCode,
  leagueCode,
  matchCode,
  onSaved,
}: Props) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const [view, setView] = useState<ScopeLimitParamsView | null>(null);
  const [matchOverrideActive, setMatchOverrideActive] = useState(false);

  const load = async () => {
    try {
      if (mode === 'match') {
        if (!matchCode) return;
        const v = await sportsApi.getLimitOverride(matchCode);
        setMatchOverrideActive(!!v.overrideActive);
        setView({
          scopeType: 'MATCH',
          scopeKey: matchCode,
          delta: Number(v.delta),
          seedPayoutYuan: Number(v.seedPayoutYuan),
          maxWorstLossYuan: Number(v.maxWorstLossYuan),
          maxBetPayoutYuan: v.maxBetPayoutYuan ?? null,
          overrideActive: !!v.overrideActive,
          inheritedDelta: Number(v.baseDelta ?? v.delta),
          inheritedSeedPayoutYuan: Number(v.globalSeedPayoutYuan ?? v.seedPayoutYuan),
          inheritedMaxWorstLossYuan: Number(v.globalMaxWorstLossYuan ?? v.maxWorstLossYuan),
          inheritedMaxBetPayoutYuan: v.globalMaxBetPayoutYuan ?? null,
          overrideDelta: v.overrideDelta ?? null,
          overrideSeedPayoutYuan: v.overrideSeedPayoutYuan ?? null,
          overrideMaxWorstLossYuan: v.overrideMaxWorstLossYuan ?? null,
          overrideMaxBetPayoutYuan: v.overrideMaxBetPayoutYuan ?? null,
          updatedBy: v.updatedBy,
          updatedAt: v.updatedAt,
        });
        form.setFieldsValue({
          delta: Number(v.delta),
          seedPayoutYuan: Number(v.seedPayoutYuan),
          maxWorstLossYuan: Number(v.maxWorstLossYuan),
          maxBetPayoutYuan:
            v.maxBetPayoutYuan != null && Number(v.maxBetPayoutYuan) > 0
              ? Number(v.maxBetPayoutYuan)
              : null,
        });
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
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, sportCode, leagueCode, matchCode]);

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
        const v = await sportsApi.putLimitOverride(matchCode, body);
        message.success(v.overrideActive ? '赛事覆盖已生效' : '已恢复继承参数');
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
      await load();
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
        await sportsApi.clearLimitOverride(matchCode);
      } else if (mode === 'league') {
        await sportsApi.clearLeagueScopeOverride(sportCode || 'football', leagueCode || 'UNKNOWN');
      } else {
        await sportsApi.clearScopeOverride(
          mode === 'overall' ? 'overall' : 'sport',
          mode === 'overall' ? '_' : sportCode || 'football',
        );
      }
      message.success('已清除本层覆盖');
      await load();
      onSaved?.();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card
      size="small"
      title={title}
      style={{ marginBottom: 16, background: 'var(--ant-color-fill-quaternary, #fafafa)' }}
      extra={
        <Space>
          <Button size="small" danger disabled={saving || !matchOverrideActive} onClick={clear}>
            清除本层覆盖
          </Button>
          <Button size="small" type="primary" loading={saving} onClick={save}>
            立即生效
          </Button>
        </Space>
      }
    >
      <Typography.Paragraph type="secondary" style={{ marginTop: 0, marginBottom: 12, fontSize: 12 }}>
        {hint || '仅改本层；下级未单独覆盖时继承本层。优先级：赛事 > 联赛 > 球类 > 总体 > 全局默认。'}
        {view?.overrideActive ? ' · 本层已覆盖' : ' · 本层未覆盖（显示继承值）'}
      </Typography.Paragraph>
      <Form form={form} layout="vertical">
        <Row gutter={16}>
          <Col xs={24} sm={12} md={6}>
            <Form.Item name="delta" label="等比例 δ" rules={[{ required: true }]} style={{ marginBottom: 8 }}>
              <InputNumber min={0} max={1} step={0.05} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Form.Item name="seedPayoutYuan" label="冷启动种子（返彩元）" rules={[{ required: true }]} style={{ marginBottom: 8 }}>
              <InputNumber min={0} step={100} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Form.Item name="maxWorstLossYuan" label="最差亏损阈值（元）" rules={[{ required: true }]} style={{ marginBottom: 8 }}>
              <InputNumber min={0} step={1000} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Form.Item name="maxBetPayoutYuan" label="单注返彩上限（元）" extra="空或 0 = 不启用" style={{ marginBottom: 8 }}>
              <InputNumber min={0} step={100} style={{ width: '100%' }} placeholder="未启用" />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Card>
  );
}
