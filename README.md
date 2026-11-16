# Recommendation service

## Claude through Amnezia XRay

Set `ANTHROPIC_API_KEY` and `AMNEZIA_XRAY_CONFIGURATION` in the ignored `documentation/.env`. The latter variable accepts an Amnezia `vpn://` share link containing a VLESS + REALITY + XHTTP client configuration. This service does not modify or install server-side configuration on the VPS.

Build from the local checkout and recreate the service without pulling or building a remote Git context:

```powershell
cd D:\alpha-kch\Recommedation-service
docker build -t kekishev/hackathon-recommendation-system-recommendation-service:latest .
cd ..\documentation
docker compose up -d --no-build recommendation-service
```

The image tag in the command matches the default `DOCKERHUB_USER` and `IMAGE_TAG` values in `documentation/docker-compose.yaml`. If those variables are set to other values, tag the local image to match the resulting Compose image name.

The service extracts the VLESS outbound, starts the bundled XRay executable with a SOCKS listener bound to `127.0.0.1` on an ephemeral port, and configures the Anthropic HTTP client to use that listener. Other service-to-service requests keep their normal network path. If the link is missing, invalid, or XRay is unavailable, Claude calls are disabled and recommendations use the existing baseline. There is no direct fallback to the Anthropic API.

For an IDE run, install an XRay executable locally and set `XRAY_EXECUTABLE` if it is not on `PATH`. The Docker image includes the official XRay binary. Keep the `vpn://` link and API key out of Git, terminal output, and logs.

The Prometheus metric `recommendation_xray_ready` is `1` when the local SOCKS listener is accepting connections, and `recommendation_xray_start_failures_total` counts startup failures. Local readiness alone does not prove that the VPS can reach Anthropic. A real recommendation request verifies the complete path but can incur an API charge.
