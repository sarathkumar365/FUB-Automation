import type { SettingsConfig } from '../contracts/settingsSchemas'

// Read-only today. A future updateConfig(command) lands here once the backend
// write API (PUT /admin/settings/config) exists.
export interface SettingsPort {
  getConfig(): Promise<SettingsConfig>
}
