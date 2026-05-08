import { z } from 'zod'
import { HttpJsonClient } from '../../../platform/adapters/http/httpJsonClient'

const RoleSchema = z.enum(['ADMIN', 'OPERATOR', 'VIEWER'])

const LoginResponseSchema = z.object({
  token: z.string().min(1),
  tokenType: z.string(),
  expiresAt: z.string(),
  username: z.string(),
  role: RoleSchema,
})

const MeResponseSchema = z.object({
  username: z.string(),
  role: RoleSchema,
})

export type LoginResponse = z.infer<typeof LoginResponseSchema>
export type MeResponse = z.infer<typeof MeResponseSchema>

export class AuthClient {
  private readonly http: HttpJsonClient

  constructor(http: HttpJsonClient = new HttpJsonClient()) {
    this.http = http
  }

  login(username: string, password: string): Promise<LoginResponse> {
    return this.http.post('/admin/auth/login', LoginResponseSchema, { username, password })
  }

  me(): Promise<MeResponse> {
    return this.http.get('/admin/auth/me', MeResponseSchema)
  }
}
