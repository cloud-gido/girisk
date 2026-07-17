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
  SafetyCertificateOutlined,
  SettingOutlined,
  ThunderboltOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { PRODUCT_NAME } from '../brand';
import { buildNavMaps, toMenuItems, type NavEntry } from './navTypes';

export const RISK_HOME = '/girisk';

export const RISK_NAVIGATION: NavEntry[] = [
  { key: '/girisk', label: '总览', icon: <DashboardOutlined /> },
  {
    key: 'monitor',
    label: '实时监控',
    icon: <RadarChartOutlined />,
    children: [
      { key: '/girisk/exposure', label: '敞口看板', icon: <FundProjectionScreenOutlined /> },
      { key: '/girisk/stream', label: '流量监控', icon: <CloudSyncOutlined /> },
      { key: '/girisk/events', label: '事件流', icon: <SafetyCertificateOutlined /> },
      { key: '/girisk/decisions', label: '决策审计', icon: <AuditOutlined /> },
      { key: '/girisk/replay', label: '风险回放', icon: <HistoryOutlined /> },
    ],
  },
  {
    key: 'policy',
    label: '策略中心',
    icon: <SettingOutlined />,
    children: [
      { key: '/girisk/config', label: '配置发布', icon: <DeploymentUnitOutlined /> },
      { key: '/girisk/rules', label: '规则管理', icon: <ProfileOutlined /> },
      { key: '/girisk/strategies', label: '策略配置', icon: <SettingOutlined /> },
      { key: '/girisk/lists', label: '黑白名单', icon: <UnorderedListOutlined /> },
    ],
  },
  { key: '/girisk/cases', label: '审核工单', icon: <FileSearchOutlined /> },
  {
    key: 'lab',
    label: '调试沙箱',
    icon: <ThunderboltOutlined />,
    children: [
      { key: '/girisk/sandbox/order', label: '订单试算', icon: <ExperimentOutlined /> },
      { key: '/girisk/sandbox/bet', label: '投注试算', icon: <ExperimentOutlined /> },
      { key: '/girisk/api-lab', label: '接口实验室', icon: <ApiOutlined /> },
    ],
  },
];

export const RISK_GROUP_LABEL: Record<string, string> = {
  monitor: '实时监控',
  policy: '策略中心',
  lab: '调试沙箱',
};

const maps = buildNavMaps(RISK_NAVIGATION);

export const riskGroupKeyForPath = (pathname: string) => maps.pathToGroup.get(pathname);

export const riskPageTitleForPath = (pathname: string) => maps.pathToLabel.get(pathname) ?? PRODUCT_NAME;
export const riskMenuItems = () => toMenuItems(RISK_NAVIGATION);
