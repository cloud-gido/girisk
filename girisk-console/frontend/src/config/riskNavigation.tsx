import {
  ApiOutlined,
  AuditOutlined,
  CloudSyncOutlined,
  DashboardOutlined,
  DeploymentUnitOutlined,
  ExperimentOutlined,
  FileSearchOutlined,
  FundProjectionScreenOutlined,
  HistoryOutlined,
  ProfileOutlined,
  RadarChartOutlined,
  SettingOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { PRODUCT_NAME } from '../brand';
import { Perm } from '../auth/session';
import { buildNavMaps, filterNavByPerm, toMenuItems, type NavEntry } from './navTypes';

export const RISK_HOME = '/girisk';

export const RISK_NAVIGATION: NavEntry[] = [
  { key: '/girisk', label: '总览', icon: <DashboardOutlined />, requiredPerm: Perm.MONITOR_READ },
  {
    key: 'monitor',
    label: '实时监控',
    icon: <RadarChartOutlined />,
    children: [
      { key: '/girisk/exposure', label: '敞口看板', icon: <FundProjectionScreenOutlined />, requiredPerm: Perm.MONITOR_READ },
      { key: '/girisk/decisions', label: '决策中心', icon: <AuditOutlined />, requiredPerm: Perm.AUDIT_READ },
      { key: '/girisk/replay', label: '风险回放', icon: <HistoryOutlined />, requiredPerm: Perm.AUDIT_READ },
      { key: '/girisk/ops-audit', label: '操作审计', icon: <FileSearchOutlined />, requiredPerm: Perm.AUDIT_READ },
    ],
  },
  {
    key: 'policy',
    label: '策略中心',
    icon: <SettingOutlined />,
    requiredPerm: Perm.CONFIG_MANAGE,
    children: [
      { key: '/girisk/config', label: '配置发布', icon: <DeploymentUnitOutlined />, requiredPerm: Perm.CONFIG_MANAGE },
      { key: '/girisk/rules', label: '规则管理', icon: <ProfileOutlined />, requiredPerm: Perm.CONFIG_MANAGE },
      { key: '/girisk/strategies', label: '策略配置', icon: <SettingOutlined />, requiredPerm: Perm.CONFIG_MANAGE },
      { key: '/girisk/lists', label: '黑白名单', icon: <UnorderedListOutlined />, requiredPerm: Perm.CONFIG_MANAGE },
    ],
  },
  { key: '/girisk/cases', label: '审核工单', icon: <FileSearchOutlined />, requiredPerm: Perm.CASE_REVIEW },
  {
    key: 'lab',
    label: '调试沙箱',
    icon: <ThunderboltOutlined />,
    requiredPerm: Perm.SANDBOX_USE,
    children: [
      { key: '/girisk/sandbox/order', label: '订单试算', icon: <ExperimentOutlined />, requiredPerm: Perm.SANDBOX_USE },
      { key: '/girisk/sandbox/bet', label: '投注试算', icon: <ExperimentOutlined />, requiredPerm: Perm.SANDBOX_USE },
      { key: '/girisk/stream', label: '管线观察', icon: <CloudSyncOutlined />, requiredPerm: Perm.SANDBOX_USE },
      { key: '/girisk/api-lab', label: '接口实验室', icon: <ApiOutlined />, requiredPerm: Perm.SANDBOX_USE },
    ],
  },
  { key: '/girisk/iam', label: '账号管理', icon: <TeamOutlined />, requiredPerm: Perm.IAM_MANAGE },
];

export const RISK_GROUP_LABEL: Record<string, string> = {
  monitor: '实时监控',
  policy: '策略中心',
  lab: '调试沙箱',
};

const maps = buildNavMaps(RISK_NAVIGATION);

export const riskGroupKeyForPath = (pathname: string) => maps.pathToGroup.get(pathname);

export const riskPageTitleForPath = (pathname: string) => maps.pathToLabel.get(pathname) ?? PRODUCT_NAME;

export const riskPermForPath = (pathname: string) => maps.pathToPerm.get(pathname);

export const riskMenuItems = () => toMenuItems(filterNavByPerm(RISK_NAVIGATION));
