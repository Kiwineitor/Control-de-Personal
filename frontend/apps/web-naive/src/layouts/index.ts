const BasicLayout = () => import('./basic.vue');
const AuthPageLayout = () => import('./auth.vue');
const AppLayout      = () => import('./app.vue');
const IFrameView = () => import('@vben/layouts').then((m) => m.IFrameView);

export { AppLayout, AuthPageLayout, BasicLayout, IFrameView };
