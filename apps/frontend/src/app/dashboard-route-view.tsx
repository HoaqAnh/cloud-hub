import { DashboardRoute } from '../features/accounts/components/dashboard-route'
import { useLocale } from '../shared/hooks/use-locale'

export default function DashboardRouteView() {
  const locale = useLocale()
  return <DashboardRoute t={locale.t} />
}
