import { useQuery } from '@tanstack/react-query'
import { useAppPorts } from '../../../app/useAppPorts'
import { queryKeys } from '../../../platform/query/queryKeys'

export function useSettingsConfigQuery() {
  const { settingsPort } = useAppPorts()

  return useQuery({
    queryKey: queryKeys.settings.config(),
    queryFn: () => settingsPort.getConfig(),
  })
}
