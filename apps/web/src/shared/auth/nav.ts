import type { Role } from './auth';

/** Single place that maps routes → roles (used by the nav and the route guard). */
export interface NavItem {
  path: string;
  labelKey: string;
  roles: Role[];
}

export const NAV: NavItem[] = [
  { path: '/crossing', labelKey: 'nav.crossing', roles: ['CROSSING_OFFICER', 'SYS_ADMIN'] },
  { path: '/profile', labelKey: 'nav.profile', roles: ['WORKER', 'DRIVER'] },
  { path: '/apply', labelKey: 'nav.apply', roles: ['WORKER', 'DRIVER', 'EMPLOYER', 'CLERK'] },
  { path: '/clerk', labelKey: 'nav.clerk', roles: ['CLERK', 'SYS_ADMIN'] },
  { path: '/employer', labelKey: 'nav.employer', roles: ['EMPLOYER'] },
  { path: '/policies', labelKey: 'nav.policies', roles: ['POLICY_ADMIN', 'SYS_ADMIN', 'AUDITOR'] },
  { path: '/enforcement', labelKey: 'nav.enforcement', roles: ['ENFORCEMENT_OFFICER', 'SYS_ADMIN', 'AUDITOR'] },
  { path: '/dashboard', labelKey: 'nav.dashboard', roles: ['SYS_ADMIN', 'AUDITOR', 'CLERK', 'ENFORCEMENT_OFFICER',
    'POLICY_ADMIN'] },
  { path: '/admin', labelKey: 'nav.admin', roles: ['SYS_ADMIN', 'AUDITOR'] },
];

export const rolesFor = (path: string): Role[] => NAV.find((n) => n.path === path)?.roles ?? [];
