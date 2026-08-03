import { Collapse, Typography } from 'antd';
import ScopeGateDutyBar from './ScopeGateDutyBar';
import ScopeLimitDutyBar from './ScopeLimitDutyBar';

type Props = {
  /** 面板标题，如「平台总体」「足球 · 球类」 */
  label: string;
  mode: 'overall' | 'sport' | 'league';
  sportCode?: string;
  leagueCode?: string;
  onSaved?: () => void;
  /** 整块默认展开 */
  defaultOpen?: boolean;
};

/**
 * 总体 / 球类 / 联赛值班配置：门控三开关 + 限额参数（可折叠）。
 */
export default function ScopeDutyConfigPanel({
  label,
  mode,
  sportCode,
  leagueCode,
  onSaved,
  defaultOpen = false,
}: Props) {
  return (
    <Collapse
      className="content-card liability-scope-duty liability-duty-fold"
      bordered={false}
      defaultActiveKey={defaultOpen ? ['scope-duty'] : []}
      style={{ marginBottom: 16 }}
      items={[{
        key: 'scope-duty',
        label: (
          <div className="liability-scope-duty-title">
            <span className="liability-duty-fold-label">{label} · 限额与停盘</span>
            <Typography.Text type="secondary" className="liability-list-hint">
              门控与限额均可按层覆盖；继承单赛事 &gt; 联赛 &gt; 球类 &gt; 默认
            </Typography.Text>
          </div>
        ),
        children: (
          <div className="liability-duty-fold-body">
            <ScopeGateDutyBar
              mode={mode}
              sportCode={sportCode}
              leagueCode={leagueCode}
              onSaved={onSaved}
              defaultOpen={false}
            />
            <ScopeLimitDutyBar
              mode={mode}
              sportCode={sportCode}
              leagueCode={leagueCode}
              title="限额参数"
              hint={
                mode === 'overall'
                  ? '平台默认；下级未覆盖时继承此处。'
                  : mode === 'sport'
                    ? '球类层；未覆盖字段继承默认。'
                    : '联赛层；未覆盖字段继承球类→默认。'
              }
              onSaved={onSaved}
              defaultOpen={false}
            />
          </div>
        ),
      }]}
    />
  );
}
