import type { MarketGroupView } from '../types';

export function marketGroupKey(g: Pick<MarketGroupView, 'marketType' | 'line'>): string {
  return `${g.marketType}::${g.line || ''}`;
}

export function stakesFromGroups(groups: MarketGroupView[]): Record<string, Record<string, number>> {
  const map: Record<string, Record<string, number>> = {};
  for (const g of groups) {
    map[marketGroupKey(g)] = { ...g.stakes };
  }
  return map;
}
