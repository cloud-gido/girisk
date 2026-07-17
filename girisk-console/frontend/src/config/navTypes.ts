import type { MenuProps } from 'antd';
import type { ReactNode } from 'react';

export interface NavLeaf {
  key: string;
  label: string;
  icon?: ReactNode;
}

export interface NavGroup {
  key: string;
  label: string;
  icon: ReactNode;
  children: NavLeaf[];
}

export type NavEntry = NavLeaf | NavGroup;

export function isNavGroup(entry: NavEntry): entry is NavGroup {
  return 'children' in entry;
}

export function buildNavMaps(entries: NavEntry[]) {
  const pathToGroup = new Map<string, string>();
  const pathToLabel = new Map<string, string>();

  for (const entry of entries) {
    if (isNavGroup(entry)) {
      for (const child of entry.children) {
        pathToGroup.set(child.key, entry.key);
        pathToLabel.set(child.key, child.label);
      }
    } else {
      pathToLabel.set(entry.key, entry.label);
    }
  }

  return { pathToGroup, pathToLabel };
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
