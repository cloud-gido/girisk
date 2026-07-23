import { Button, Collapse, Space, Switch, Tag, Typography, message } from 'antd';
import { useEffect, useState } from 'react';
import { sportsApi } from '../api/sportsClient';
import { getUser, hasPerm, Perm } from '../auth/session';
import type { ScopeGateOverrideRequest, ScopeGateParamsView } from '../types';

type Props = {
  mode: 'overall' | 'sport' | 'league' | 'match';
  sportCode?: string;
  leagueCode?: string;
  matchCode?: string;
  onSaved?: () => void;
  /** 默认展开 */
  defaultOpen?: boolean;
};

const SOURCE_LABEL: Record<string, string> = {
  DEFAULT: '系统默认',
  OVERALL: '默认层',
  SPORT: '球类',
  LEAGUE: '联赛',
  MATCH: '单赛事',
  MATCH_STATUS: '赛事停盘',
};

function src(s: string) {
  return SOURCE_LABEL[s] || s;
}

/**
 * 总开关 / 限额开关 / 敞口开关。继承：单赛事 > 联赛 > 球类 > 默认。
 * 点开关即写入本层覆盖；未点过的字段保持 null（继续继承）。
 */
export default function ScopeGateDutyBar({
  mode,
  sportCode,
  leagueCode,
  matchCode,
  onSaved,
  defaultOpen = false,
}: Props) {
  const [view, setView] = useState<ScopeGateParamsView | null>(null);
  const [saving, setSaving] = useState(false);

  const load = async () => {
    try {
      const v =
        mode === 'match'
          ? await sportsApi.getMatchGates(matchCode || '')
          : mode === 'league'
            ? await sportsApi.getLeagueGates(sportCode || 'football', leagueCode || 'UNKNOWN')
            : await sportsApi.getScopeGates(
                mode === 'overall' ? 'overall' : 'sport',
                mode === 'overall' ? '_' : sportCode || 'football',
              );
      setView(v);
    } catch (e) {
      message.error((e as Error).message);
    }
  };

  useEffect(() => {
    if (mode === 'match' && !matchCode) return;
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, sportCode, leagueCode, matchCode]);

  const canWrite = view?.canWrite
    ?? (hasPerm(Perm.DUTY_WRITE_GLOBAL)
      || ((mode === 'league' || mode === 'match') && hasPerm(Perm.DUTY_WRITE_MATCH)));

  const save = async (patch: ScopeGateOverrideRequest) => {
    if (!view || !canWrite) {
      message.warning('当前账号无权修改本层开关');
      return;
    }
    setSaving(true);
    try {
      const body: ScopeGateOverrideRequest = {
        tradingEnabled:
          patch.tradingEnabled !== undefined ? patch.tradingEnabled : (view.overrideTradingEnabled ?? null),
        limitGateEnabled:
          patch.limitGateEnabled !== undefined ? patch.limitGateEnabled : (view.overrideLimitGateEnabled ?? null),
        exposureGateEnabled:
          patch.exposureGateEnabled !== undefined
            ? patch.exposureGateEnabled
            : (view.overrideExposureGateEnabled ?? null),
        operatorId: getUser()?.username || 'trader',
      };
      const v =
        mode === 'match'
          ? await sportsApi.putMatchGates(matchCode || '', body)
          : mode === 'league'
            ? await sportsApi.putLeagueGates(sportCode || 'football', leagueCode || 'UNKNOWN', body)
            : await sportsApi.putScopeGates(
                mode === 'overall' ? 'overall' : 'sport',
                mode === 'overall' ? '_' : sportCode || 'football',
                body,
              );
      setView(v);
      message.success('门控已更新');
      onSaved?.();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const clear = async () => {
    if (!canWrite) return;
    setSaving(true);
    try {
      const v =
        mode === 'match'
          ? await sportsApi.clearMatchGates(matchCode || '')
          : mode === 'league'
            ? await sportsApi.clearLeagueGates(sportCode || 'football', leagueCode || 'UNKNOWN')
            : await sportsApi.clearScopeGates(
                mode === 'overall' ? 'overall' : 'sport',
                mode === 'overall' ? '_' : sportCode || 'football',
              );
      setView(v);
      message.success('已清除本层门控覆盖');
      onSaved?.();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSaving(false);
    }
  };

  const tradingOff = !view?.tradingEnabled;

  return (
    <Collapse
      className="liability-duty-module"
      bordered={false}
      defaultActiveKey={defaultOpen ? ['gates'] : []}
      items={[{
        key: 'gates',
        label: (
          <span className="liability-duty-module-label">
            门控开关
            {view ? (
              <Typography.Text type="secondary" style={{ marginLeft: 8, fontWeight: 400 }}>
                {view.tradingEnabled ? '开盘' : '停盘'}
                {' · '}
                限额{view.limitGateEnabled ? '开' : '关'}
                {' · '}
                敞口{view.exposureGateEnabled ? '开' : '关'}
              </Typography.Text>
            ) : null}
          </span>
        ),
        extra: (
          <Button
            size="small"
            danger
            disabled={saving || !canWrite || !view?.overrideActive}
            onClick={(e) => {
              e.stopPropagation();
              clear();
            }}
          >
            清除本层覆盖
          </Button>
        ),
        children: !view ? (
          <Typography.Text type="secondary">加载中…</Typography.Text>
        ) : (
          <>
            <Typography.Paragraph type="secondary" style={{ marginTop: 0, marginBottom: 12, fontSize: 12 }}>
              继承：单赛事 &gt; 联赛 &gt; 球类 &gt; 默认。
              {view.overrideActive ? ' · 本层已覆盖' : ' · 本层未覆盖（显示继承值）'}
              {!canWrite ? ' · 只读' : ''}
            </Typography.Paragraph>
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              <div className="liability-gate-row">
                <div>
                  <Typography.Text strong>总开关</Typography.Text>
                  <Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                    来源 {src(view.tradingSource)}
                  </Typography.Text>
                </div>
                <Space>
                  <Tag color={view.tradingEnabled ? 'success' : 'error'}>
                    {view.tradingEnabled ? '开盘' : '停盘'}
                  </Tag>
                  <Switch
                    checked={view.tradingEnabled}
                    loading={saving}
                    disabled={!canWrite}
                    onChange={(checked) => save({ tradingEnabled: checked })}
                  />
                </Space>
              </div>
              <div className="liability-gate-row">
                <div>
                  <Typography.Text strong>限额开关</Typography.Text>
                  <Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                    Gate0/1 · 来源 {src(view.limitGateSource)}
                  </Typography.Text>
                </div>
                <Space>
                  <Tag color={view.limitGateEnabled ? 'processing' : 'default'}>
                    {view.limitGateEnabled ? '拦截' : '跳过'}
                  </Tag>
                  <Switch
                    checked={view.limitGateEnabled}
                    loading={saving}
                    disabled={!canWrite || tradingOff}
                    onChange={(checked) => save({ limitGateEnabled: checked })}
                  />
                </Space>
              </div>
              <div className="liability-gate-row">
                <div>
                  <Typography.Text strong>敞口开关</Typography.Text>
                  <Typography.Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>
                    Gate2 · 来源 {src(view.exposureGateSource)}
                  </Typography.Text>
                </div>
                <Space>
                  <Tag color={view.exposureGateEnabled ? 'processing' : 'default'}>
                    {view.exposureGateEnabled ? '拦截' : '跳过'}
                  </Tag>
                  <Switch
                    checked={view.exposureGateEnabled}
                    loading={saving}
                    disabled={!canWrite || tradingOff}
                    onChange={(checked) => save({ exposureGateEnabled: checked })}
                  />
                </Space>
              </div>
            </Space>
          </>
        ),
      }]}
    />
  );
}
