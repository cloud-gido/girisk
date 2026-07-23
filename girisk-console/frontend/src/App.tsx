import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Perm } from './auth/session';
import ProtectedRoute from './components/ProtectedRoute';
import RequirePerm from './components/RequirePerm';
import RiskLayout from './components/RiskLayout';
import ApiLabPage from './pages/ApiLabPage';
import CasesPage from './pages/CasesPage';
import ConfigCenterPage from './pages/ConfigCenterPage';
import DashboardPage from './pages/DashboardPage';
import DecisionsPage from './pages/DecisionsPage';
import ForbiddenPage from './pages/ForbiddenPage';
import IamPage from './pages/IamPage';
import LiabilityBoardPage from './pages/LiabilityBoardPage';
import ListsPage from './pages/ListsPage';
import LoginPage from './pages/LoginPage';
import OpsAuditPage from './pages/OpsAuditPage';
import OrderSandboxPage from './pages/OrderSandboxPage';
import ReplayPage from './pages/ReplayPage';
import RulesPage from './pages/RulesPage';
import SportsBetPage from './pages/SportsBetPage';
import StrategiesPage from './pages/StrategiesPage';
import StreamPage from './pages/StreamPage';

function LegacyRedirect({ to }: { to: string }) {
  return <Navigate to={to} replace />;
}

export default function App() {
  return (
    <ConfigProvider locale={zhCN} theme={{ token: { colorPrimary: '#1677ff', borderRadius: 8 } }}>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute loginPath="/login" />}>
            <Route element={<RiskLayout />}>
              <Route element={<RequirePerm perm={Perm.MONITOR_READ} />}>
                <Route path="/girisk" element={<DashboardPage />} />
                <Route path="/girisk/exposure" element={<LiabilityBoardPage />} />
              </Route>
              <Route path="/girisk/liability" element={<LegacyRedirect to="/girisk/exposure" />} />

              <Route element={<RequirePerm perm={Perm.SANDBOX_USE} />}>
                <Route path="/girisk/stream" element={<StreamPage />} />
                <Route path="/girisk/sandbox/order" element={<OrderSandboxPage />} />
                <Route path="/girisk/sandbox/bet" element={<SportsBetPage />} />
                <Route path="/girisk/api-lab" element={<ApiLabPage />} />
              </Route>
              <Route path="/girisk/sandbox" element={<LegacyRedirect to="/girisk/sandbox/order" />} />
              <Route path="/girisk/evaluate" element={<LegacyRedirect to="/girisk/sandbox/order" />} />
              <Route path="/girisk/events" element={<LegacyRedirect to="/girisk/stream" />} />

              <Route element={<RequirePerm perm={Perm.AUDIT_READ} />}>
                <Route path="/girisk/decisions" element={<DecisionsPage />} />
                <Route path="/girisk/replay" element={<ReplayPage />} />
                <Route path="/girisk/ops-audit" element={<OpsAuditPage />} />
              </Route>

              <Route element={<RequirePerm perm={Perm.CONFIG_MANAGE} />}>
                <Route path="/girisk/config" element={<ConfigCenterPage />} />
                <Route path="/girisk/rules" element={<RulesPage />} />
                <Route path="/girisk/strategies" element={<StrategiesPage />} />
                <Route path="/girisk/lists" element={<ListsPage />} />
              </Route>

              <Route element={<RequirePerm perm={Perm.CASE_REVIEW} />}>
                <Route path="/girisk/cases" element={<CasesPage />} />
              </Route>

              <Route element={<RequirePerm perm={Perm.IAM_MANAGE} />}>
                <Route path="/girisk/iam" element={<IamPage />} />
              </Route>

              <Route path="/girisk/forbidden" element={<ForbiddenPage />} />
            </Route>
            <Route path="/" element={<Navigate to="/girisk" replace />} />
            <Route path="/stream" element={<LegacyRedirect to="/girisk/stream" />} />
            <Route path="/evaluate" element={<LegacyRedirect to="/girisk/sandbox/order" />} />
            <Route path="/events" element={<LegacyRedirect to="/girisk/stream" />} />
            <Route path="/decisions" element={<LegacyRedirect to="/girisk/decisions" />} />
            <Route path="/rules" element={<LegacyRedirect to="/girisk/rules" />} />
            <Route path="/strategies" element={<LegacyRedirect to="/girisk/strategies" />} />
            <Route path="/lists" element={<LegacyRedirect to="/girisk/lists" />} />
            <Route path="/cases" element={<LegacyRedirect to="/girisk/cases" />} />
            <Route path="/api-lab" element={<LegacyRedirect to="/girisk/api-lab" />} />
            <Route path="/sports" element={<LegacyRedirect to="/girisk/exposure" />} />
            <Route path="/sports/exposure" element={<LegacyRedirect to="/girisk/exposure" />} />
            <Route path="/sports/bet" element={<LegacyRedirect to="/girisk/sandbox/bet" />} />
            <Route path="/sports-bet" element={<LegacyRedirect to="/girisk/sandbox/bet" />} />
          </Route>
          <Route path="*" element={<Navigate to="/girisk" replace />} />
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  );
}
