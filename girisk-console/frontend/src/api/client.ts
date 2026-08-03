import { login, me } from './auth';
import { riskApi } from './riskClient';
import { sportsApi } from './sportsClient';

/** @deprecated 请优先使用 riskApi / sportsApi / auth */
export const api = {
  login,
  me,
  dashboard: riskApi.dashboard,
  decide: riskApi.decide,
  confirmOrder: riskApi.confirmOrder,
  cancelOrder: riskApi.cancelOrder,
  settleOrder: riskApi.settleOrder,
  orderStatus: riskApi.orderStatus,
  evaluate: riskApi.evaluate,
  strategies: riskApi.strategies,
  rules: riskApi.rules,
  createRule: riskApi.createRule,
  toggleRule: riskApi.toggleRule,
  deleteRule: riskApi.deleteRule,
  lists: riskApi.lists,
  createList: riskApi.createList,
  deleteList: riskApi.deleteList,
  cases: riskApi.cases,
  reviewCase: riskApi.reviewCase,
  decisions: riskApi.decisions,
  decisionDetail: riskApi.decisionDetail,
  decisionsByOrder: riskApi.decisionsByOrder,
  decisionsByFixture: riskApi.decisionsByFixture,
  replayByOrder: riskApi.replayByOrder,
  replayByTrace: riskApi.replayByTrace,
  configReleases: riskApi.configReleases,
  configCurrent: riskApi.configCurrent,
  createConfigRelease: riskApi.createConfigRelease,
  submitConfigRelease: riskApi.submitConfigRelease,
  approveConfigRelease: riskApi.approveConfigRelease,
  rejectConfigRelease: riskApi.rejectConfigRelease,
  publishConfigRelease: riskApi.publishConfigRelease,
  fixtures: riskApi.fixtures,
  fixturesTop: riskApi.fixturesTop,
  events: riskApi.events,
  streamStatus: riskApi.streamStatus,
  mockOrder: riskApi.mockOrder,
  mockBurst: riskApi.mockBurst,
  sportsMatches: sportsApi.matches,
  sportsMatch: sportsApi.match,
  sportsExposureCheck: sportsApi.exposureCheck,
  sportsBetEvaluate: sportsApi.betEvaluate,
  sportsBets: sportsApi.bets,
};

export { login, me } from './auth';
export { riskApi } from './riskClient';
export { sportsApi } from './sportsClient';
