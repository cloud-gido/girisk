import { PRODUCT_NAME, PRODUCT_SUBTITLE } from '../brand';
import {
  RISK_GROUP_LABEL,
  RISK_HOME,
  riskGroupKeyForPath,
  riskMenuItems,
  riskPageTitleForPath,
} from '../config/riskNavigation';
import ShellLayout from './ShellLayout';

export default function RiskLayout() {
  return (
    <ShellLayout
      homePath={RISK_HOME}
      homeLabel="总览"
      productName={PRODUCT_NAME}
      productSubtitle={PRODUCT_SUBTITLE}
      menuItems={riskMenuItems()}
      groupKeyForPath={riskGroupKeyForPath}
      pageTitleForPath={riskPageTitleForPath}
      groupLabels={RISK_GROUP_LABEL}
    />
  );
}
