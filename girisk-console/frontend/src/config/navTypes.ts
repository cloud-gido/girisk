import type { MenuProps } from 'antd';
import type { ReactNode } from 'react';
import { hasPerm } from '../auth/session';

export interface NavLeaf {
  key: string;
  label: string;
  icon?: ReactNode;
  /** 需要的权限码；缺省则登录即可 */
  requiredPerm?: string;
}

export interface NavGroup {
  key: string;
  label: string;
  icon: ReactNode;
  children: NavLeaf[];
  requiredPerm?: string;
}

export type NavEntry = NavLeaf | NavGroup;

export function isNavGroup(entry: NavEntry): entry is NavGroup {
  return 'children' in entry;
}

export function filterNavByPerm(entries: NavEntry[]): NavEntry[] {
  const out: NavEntry[] = [];
  for (const entry of entries) {
    if (isNavGroup(entry)) {
      if (entry.requiredPerm && !hasPerm(entry.requiredPerm)) {
        continue;
      }
      const children = entry.children.filter((c) => !c.requiredPerm || hasPerm(c.requiredPerm));
      if (children.length === 0) {
        continue;
      }
      out.push({ ...entry, children });
    } else {
      if (entry.requiredPerm && !hasPerm(entry.requiredPerm)) {
        continue;
      }
      out.push(entry);
    }
  }
  return out;
}

export function buildNavMaps(entries: NavEntry[]) {
  const pathToGroup = new Map<string, string>();
  const pathToLabel = new Map<string, string>();
  const pathToPerm = new Map<string, string>();

  for (const entry of entries) {
    if (isNavGroup(entry)) {
      for (const child of entry.children) {
        pathToGroup.set(child.key, entry.key);
        pathToLabel.set(child.key, child.label);
        if (child.requiredPerm) pathToPerm.set(child.key, child.requiredPerm);
      }
    } else {
      pathToLabel.set(entry.key, entry.label);
      if (entry.requiredPerm) pathToPerm.set(entry.key, entry.requiredPerm);
    }
  }

  return { pathToGroup, pathToLabel, pathToPerm };
}

export function toMenuItems(entries: NavEntry[]): MenuProps['items'] {
  return entries.map((entry) => {
    if (isNavGroup(entry)) {
      return {
        key: entry.key,
        icon: entry.icon,
        label: entry.label,
        children: entry.children.map((c) => ({
          key: c.key,
          icon: c.icon,
          label: c.label,
        })),
      };
    }
    return { key: entry.key, icon: entry.icon, label: entry.label };
  });
}
