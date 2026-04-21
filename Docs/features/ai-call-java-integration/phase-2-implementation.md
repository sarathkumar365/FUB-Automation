# AI Call Java Integration — Pass 2 Implementation

## Date
2026-04-21

## Scope completed
- Added typed call-service port:
  - `AiCallServiceClient`
  - `PlaceCallRequest` / `PlaceCallResponse`
  - `GetCallResponse`
  - `AiCallServiceClientException`
- Added HTTP adapter implementation:
  - `AiCallServiceHttpClientAdapter`
- Added transport DTOs:
  - `AiCallPlaceRequestDto`
  - `AiCallPlaceResponseDto`
  - `AiCallStatusResponseDto`
- Added configuration:
  - `AiCallServiceProperties`
  - `AiCallServiceConfig`
  - `application.properties` entries for `ai-call-service.base-url`, `connect-timeout-ms`, `read-timeout-ms`
- Added adapter test suite:
  - `AiCallServiceHttpClientAdapterTest`

## Behavior now available
- Java runtime has a dedicated typed client for the Python call-service contract endpoints:
  - `POST /call`
  - `GET /calls/{callSid}`
- Adapter returns explicit in-progress vs terminal call status shapes.
- Error behavior is normalized:
  - 5xx -> transient
  - 4xx -> non-transient
  - network failures -> transient

## Remaining work
- Implement `AiCallWorkflowStep` using this adapter (Pass 3).
- Wire catalog/runtime integration and regression gates (Pass 4).
