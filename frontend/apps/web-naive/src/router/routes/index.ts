import type { RouteRecordRaw } from 'vue-router';
import { mergeRouteModules, traverseTreeValues } from '@vben/utils';
import { coreRoutes, fallbackNotFoundRoute } from './core';

const dynamicRouteFiles = import.meta.glob('./modules/**/*.ts', { eager: true });
const dynamicRoutes: RouteRecordRaw[] = mergeRouteModules(dynamicRouteFiles);
const staticRoutes: RouteRecordRaw[]  = [];
const externalRoutes: RouteRecordRaw[] = [];

const routes: RouteRecordRaw[] = [
  ...coreRoutes,
  ...externalRoutes,
  fallbackNotFoundRoute,
];

const coreRouteNames = traverseTreeValues(coreRoutes, (route) => route.name);
const accessRoutes   = [...dynamicRoutes, ...staticRoutes];

export { accessRoutes, coreRouteNames, routes };