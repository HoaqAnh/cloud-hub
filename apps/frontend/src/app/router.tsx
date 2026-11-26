import { lazy, Suspense } from 'react'
import {
  createRootRoute,
  createRoute,
  createRouter,
  Navigate,
  RouterProvider,
} from '@tanstack/react-router'
import { AppRoot } from './app-root'
import { useLocale } from '../shared/hooks/use-locale'

const DashboardRouteView = lazy(() => import('./dashboard-route-view'))
const ExplorerRouteView = lazy(() => import('./explorer-route-view'))

function RouteLoading() {
  const { t } = useLocale()
  return (
    <div className="flex items-center justify-center gap-2 py-20 text-sm text-slate-500">
      <span className="size-4 animate-spin rounded-full border-2 border-violet-200 border-t-violet-600" />
      {t('loading')}...
    </div>
  )
}

const rootRoute = createRootRoute({
  component: AppRoot,
  notFoundComponent: () => <Navigate to="/dashboard" replace />,
})

const indexRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: () => <Navigate to="/dashboard" replace />,
})

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  component: () => null,
})

const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard',
  component: () => (
    <Suspense fallback={<RouteLoading />}>
      <DashboardRouteView />
    </Suspense>
  ),
})

const explorerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/explorer',
  component: () => (
    <Suspense fallback={<RouteLoading />}>
      <ExplorerRouteView />
    </Suspense>
  ),
})

const routeTree = rootRoute.addChildren([
  indexRoute,
  loginRoute,
  dashboardRoute,
  explorerRoute,
])

const router = createRouter({ routeTree })

export function AppRouter() {
  return <RouterProvider router={router} />
}

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
