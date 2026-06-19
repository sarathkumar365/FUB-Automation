import { projectSettingsConfig } from './settingsProjection'
import {
  settingsConfigResponseSchema,
  type SettingsConfig,
} from '../../contracts/settingsSchemas'
import type { SettingsPort } from '../../ports/settingsPort'
import type { HttpJsonClient } from './httpJsonClient'

const SETTINGS_CONFIG_PATH = '/admin/settings/config'

export class HttpSettingsAdapter implements SettingsPort {
  private readonly httpClient: HttpJsonClient

  constructor(httpClient: HttpJsonClient) {
    this.httpClient = httpClient
  }

  async getConfig(): Promise<SettingsConfig> {
    const response = await this.httpClient.get(SETTINGS_CONFIG_PATH, settingsConfigResponseSchema)
    return projectSettingsConfig(response)
  }
}
